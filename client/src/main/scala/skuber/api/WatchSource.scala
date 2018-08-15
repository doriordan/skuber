package skuber.api

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Source}
import akka.stream.{Materializer, SourceShape}
import play.api.libs.json.Format
import skuber.api.client._
import skuber.{K8SRequestContext, ObjectResource, ResourceDefinition}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

private[api] object WatchSource {
  sealed trait StreamElement[O <: ObjectResource] {}
  case class End[O <: ObjectResource]() extends StreamElement[O]
  case class Start[O <: ObjectResource](resourceVersion: Option[String]) extends StreamElement[O]
  case class Result[O <: ObjectResource](resourceVersion: String, value: WatchEvent[O]) extends StreamElement[O]

  sealed trait StreamState {}
  case object Waiting extends StreamState
  case object Processing extends StreamState
  case object Finished extends StreamState

  case class StreamContext(currentResourceVersion: Option[String], state: StreamState)

  private val timeoutSecondsQueryParam: String = "timeoutSeconds"
  private val resourceVersionQueryParam: String = "resourceVersion"

  def apply[O <: ObjectResource](rc: K8SRequestContext,
                                 pool: Pool[Start[O]],
                                 name: Option[String],
                                 sinceResourceVersion: Option[String],
                                 maxWatchRequestTimeout: Duration,
                                 bufSize: Int)(implicit sys: ActorSystem,
                                               fm: Materializer,
                                               format: Format[O],
                                               rd: ResourceDefinition[O],
                                               lc: LoggingContext): Source[WatchEvent[O], NotUsed] = {
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      implicit val dispatcher: ExecutionContext = sys.dispatcher

      def createRequest(rc: RequestContext, name: Option[String], sinceResourceVersion: Option[String], rd: ResourceDefinition[O], timeout: Duration) = {
        rc.buildRequest(
          HttpMethods.GET, rd, name,
          query = Some(Uri.Query(timeoutSecondsQueryParam -> timeout.toSeconds.toString :: sinceResourceVersion.map(v => resourceVersionQueryParam -> v).toList: _*)),
          watch = true
        )
      }

      val singleEnd = Source.single(End[O]())

      def singleStart(s:StreamElement[O]) = Source.single(s)

      val initSource = Source.single(
        (createRequest(rc, name, sinceResourceVersion, rd, maxWatchRequestTimeout), Start[O](sinceResourceVersion))
      )

      val httpFlow: Flow[(HttpRequest, Start[O]), StreamElement[O], NotUsed] =
        Flow[(HttpRequest, Start[O])].map { request => // log request
          rc.logInfo(rc.logConfig.logRequestBasic, s"about to send HTTP request: ${request._1.method.value} ${request._1.uri.toString}")
          request
        }.via(pool).flatMapConcat {
          case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), se) =>
            rc.logInfo(rc.logConfig.logResponseBasic, s"received response with HTTP status 200")
            singleStart(se).concat(
              BytesToWatchEventSource[O](entity.dataBytes, bufSize).map { event =>
                Result[O](event._object.resourceVersion, event)
              }
            ).concat(singleEnd)
          case (Success(HttpResponse(sc, _, entity, _)), _) =>
            rc.logWarn(s"Error watching resource. Recieved a status of ${sc.intValue()}")
            entity.discardBytes()
            throw new K8SException(Status(message = Some("Error watching resource."), code = Some(sc.intValue())))
          case (Failure(f), _) =>
            rc.logError("Error watching resource.", f)
            throw new K8SException(Status(message = Some("Error watching resource.")))
        }

      val outboundFlow: Flow[StreamElement[O], WatchEvent[O], NotUsed] =
        Flow[StreamElement[O]]
          .filter(_.isInstanceOf[Result[O]])
          .map{
            case Result(_, event) => event
            case _ => throw new K8SException(Status(message = Some("Error processing watch events.")))
          }

      val feedbackFlow: Flow[StreamElement[O], (HttpRequest, Start[O]), NotUsed] =
        Flow[StreamElement[O]].scan(StreamContext(None, Waiting)){(cxt, next) =>
          next match {
            case Start(rv) => StreamContext(rv, Processing)
            case Result(rv, _) => StreamContext(Some(rv), Processing)
            case End() => cxt.copy(state = Finished)
          }
        }.filter(_.state == Finished).map { acc =>
          (createRequest(rc, name, acc.currentResourceVersion, rd, maxWatchRequestTimeout), Start[O](acc.currentResourceVersion))
        }

      val init = b.add(initSource)
      val http = b.add(httpFlow)
      val merge = b.add(Merge[(HttpRequest, Start[O])](2))
      val broadcast = b.add(Broadcast[StreamElement[O]](2, eagerCancel = true))
      val outbound = b.add(outboundFlow)
      val feedback = b.add(feedbackFlow)

      init ~> merge ~> http     ~> broadcast ~> outbound
              merge <~ feedback <~ broadcast

      SourceShape(outbound.out)
    })
  }
}
