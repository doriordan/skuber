package skuber.api

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, JsonFraming, Merge, Source}
import akka.stream.{Materializer, SourceShape}
import akka.util.ByteString
import play.api.libs.json.{Format, JsError, JsSuccess, Json}
import skuber.api.client._
import skuber.{ObjectResource, ResourceDefinition}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration._

object WatchSource {
  private sealed trait StreamElement[O <: ObjectResource] {}
  private case class End[O <: ObjectResource]() extends StreamElement[O]
  private case class Request[O <: ObjectResource](resourceVersion: Option[String]) extends StreamElement[O]
  private case class Result[O <: ObjectResource](resourceVersion: String, value: WatchEvent[O]) extends StreamElement[O]

  private sealed trait StreamState {}
  private case object Waiting extends StreamState
  private case object Processing extends StreamState
  private case object Finished extends StreamState

  private case class StreamContext(currentResourceVersion: Option[String], state: StreamState)

  def apply[O <: ObjectResource](rc: RequestContext,
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

      implicit val dispatcher: ExecutionContextExecutor = sys.dispatcher

      val initSource = Source.single(
        (Request[O](sinceResourceVersion), createRequest(rc, name, sinceResourceVersion, rd, maxWatchRequestTimeout))
      )

      val httpFlow: Flow[(Request[O], HttpRequest), StreamElement[O], _] =
        Flow[(Request[O], HttpRequest)].mapAsync(1) { request =>
          rc.invoke(request._2, watch = true).map(response => (request._1, response))
        }.mapMaterializedValue(_ => NotUsed).flatMapConcat {
          case (r, HttpResponse(StatusCodes.OK, _, entity, _)) =>
            val start: Source[StreamElement[O], _] = Source.single(r)
            val results: Source[StreamElement[O], _] = bytesSourceToWatchEventSource(entity.dataBytes, bufSize)
            val end: Source[StreamElement[O], _] = Source.single(End[O]())
            start ++ results ++ end
          case (r, HttpResponse(sc, _, entity, _)) =>
            throw new K8SException(Status(message = Some("Error watching resource"), code = Some(sc.intValue())))
        }

      val outboundFlow: Flow[StreamElement[O], WatchEvent[O], NotUsed] =
        Flow[StreamElement[O]]
          .filter(_.isInstanceOf[Result[O]])
          .map{
            case Result(_, event) => event
            case _ => throw new K8SException(Status(message = Some("Error processing watch events.")))
          }

      val feedbackFlow: Flow[StreamElement[O], (Request[O], HttpRequest), NotUsed] =
        Flow[StreamElement[O]].scan(StreamContext(None, Waiting)){(cxt, next) =>
          next match {
            case Request(rv) => StreamContext(rv, Processing)
            case Result(rv, _) => StreamContext(Some(rv), Processing)
            case End() => cxt.copy(state = Finished)
          }
        }.filter(_.state == Finished).map { acc =>
          (Request[O](acc.currentResourceVersion), createRequest[O](rc, name, acc.currentResourceVersion, rd, maxWatchRequestTimeout))
        }

      val init = b.add(initSource)
      val http = b.add(httpFlow)
      val merge = b.add(Merge[(Request[O], HttpRequest)](2))
      val broadcast = b.add(Broadcast[StreamElement[O]](2))
      val outbound = b.add(outboundFlow)
      val feedback = b.add(feedbackFlow)

      // format: OFF
      init ~> merge ~> http     ~> broadcast ~> outbound
              merge <~ feedback <~ broadcast
      // format: ON

      SourceShape(outbound.out)
    })
  }

  private def createRequest[O <: ObjectResource](rc: RequestContext, name: Option[String], sinceResourceVersion: Option[String], rd: ResourceDefinition[O], timeout: Duration) = {
    rc.buildRequest(HttpMethods.GET, rd, name, query = createQueryParams(sinceResourceVersion, timeout), watch = true)
  }

  private def createQueryParams[O <: ObjectResource](sinceResourceVersion: Option[String], timeout: Duration) = {
    Some(Uri.Query("timeoutSeconds" -> timeout.toSeconds.toString :: sinceResourceVersion.map(v => "resourceVersion" -> v).toList: _*))
  }

  private def bytesSourceToWatchEventSource[O <: ObjectResource](bytesSource: Source[ByteString, _],
                                                                      bufSize: Int)(implicit ec: ExecutionContext,
                                                                                    format: Format[O],
                                                                                    lc: LoggingContext): Source[Result[O], _] = {
    import skuber.json.format.apiobj.watchEventFormat
    bytesSource
      .via(JsonFraming.objectScanner(bufSize))
      .map { singleEventBytes =>
        val singleEventJson = Json.parse(singleEventBytes.utf8String)
        val validatedEvent = singleEventJson.validate[WatchEvent[O]](watchEventFormat[O])
        validatedEvent match {
          case JsSuccess(value, _) => value
          case JsError(e) => throw new K8SException(Status(message = Some("Error parsing watched object"), details = Some(s"${lc.output} - ${e.toString}")))
        }
      }.map { event =>
        Result[O](event._object.resourceVersion, event)
      }
  }
}
