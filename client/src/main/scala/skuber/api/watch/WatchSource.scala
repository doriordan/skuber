package skuber.api.watch

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, Source}
import akka.stream.{Materializer, SourceShape}
import play.api.libs.json.Format
import skuber.api.client._
import skuber.api.client.impl.KubernetesClientImpl
import skuber.{ListOptions, ObjectResource, ResourceDefinition}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
  * @author David O'Riordan
  */
object WatchSource {
  private[api] sealed trait StreamElement[O <: ObjectResource] {}
  private[api] case class End[O <: ObjectResource]() extends StreamElement[O]
  case class Start[O <: ObjectResource](resourceVersion: Option[String]) extends StreamElement[O]
  private[api] case class Result[O <: ObjectResource](resourceVersion: String, value: WatchEvent[O]) extends StreamElement[O]

  private[api] sealed trait StreamState {}
  private[api] case object Waiting extends StreamState
  private[api] case object Processing extends StreamState
  private[api] case object Finished extends StreamState

  private[api] case class StreamContext(currentResourceVersion: Option[String], state: StreamState)

  private[api] def apply[O <: ObjectResource](client: KubernetesClientImpl,
                                 pool: Pool[Start[O]],
                                 name: Option[String],
                                 options: ListOptions,
                                 bufSize: Int)(implicit sys: ActorSystem,
                                               fm: Materializer,
                                               format: Format[O],
                                               rd: ResourceDefinition[O],
                                               lc: LoggingContext): Source[WatchEvent[O], (Pool[WatchSource.Start[O]], Option[Http.HostConnectionPool])] = {

    implicit val dispatcher: ExecutionContext = sys.dispatcher

    val singleEnd = Source.single(End[O]())

    def singleStart(s:StreamElement[O]) = Source.single(s)

    val httpFlow: Flow[(HttpRequest, Start[O]), StreamElement[O], Option[Http.HostConnectionPool]] =
      Flow[(HttpRequest, Start[O])].map { request => // log request
        client.logInfo(client.logConfig.logRequestBasic, s"about to send HTTP request: ${request._1.method.value} ${request._1.uri.toString}")
        request
      }.viaMat[(Try[HttpResponse], Start[O]), Option[Http.HostConnectionPool], Option[Http.HostConnectionPool]](pool)(Keep.right).flatMapConcat {
        case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), se) =>
          client.logInfo(client.logConfig.logResponseBasic, s"received response with HTTP status 200")
          singleStart(se).concat(
            BytesToWatchEventSource[O](entity.dataBytes, bufSize).map { event =>
              Result[O](event._object.resourceVersion, event)
            }
          ).concat(singleEnd)
        case (Success(HttpResponse(sc, _, entity, _)), _) =>
          client.logWarn(s"Error watching resource. Received a status of ${sc.intValue()}")
          entity.discardBytes()
          throw new K8SException(Status(message = Some("Error watching resource"), code = Some(sc.intValue())))
        case (Failure(f), _) =>
          client.logError("Error watching resource.", f)
          throw new K8SException(Status(message = Some("Error watching resource"), details = Some(f.getMessage)))
      }

    val httpFlowMat: Flow[(HttpRequest, Start[O]), StreamElement[O], (Pool[WatchSource.Start[O]], Option[Http.HostConnectionPool])] =
      httpFlow.mapMaterializedValue { pool -> _ }

    def createWatchRequest(since: Option[String]) =
    {
      val nameFieldSelector=name.map(objName => s"metadata.name=$objName")
      val watchOptions=options.copy(
        resourceVersion = since,
        watch = Some(true),
        fieldSelector = nameFieldSelector.orElse(options.fieldSelector)
      )
      client.buildRequest(
        HttpMethods.GET, rd, None, query =  Some(Uri.Query(watchOptions.asMap))
      )
    }

    val initSource = Source.single(
      (createWatchRequest(options.resourceVersion), Start[O](options.resourceVersion))
    )

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
        (createWatchRequest(acc.currentResourceVersion), Start[O](acc.currentResourceVersion))
      }

    Source.fromGraph(GraphDSL.create(httpFlowMat) { implicit b => http =>
      import GraphDSL.Implicits._

      val init = b.add(initSource)
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
