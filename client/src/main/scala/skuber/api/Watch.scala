package skuber.api


import akka.http.scaladsl.model.HttpMethods
import skuber.{ObjectResource, ResourceDefinition}
import skuber.json.format.apiobj.watchEventFormat
import skuber.api.client.{K8SException, RequestContext, Status, WatchEvent, LoggingContext}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.util.ByteString
import akka.stream.scaladsl.{JsonFraming, Source}
import play.api.libs.json.{Format, JsError, JsSuccess, Json}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import org.slf4j.LoggerFactory

import scala.language.postfixOps

/**
 * @author David O'Riordan
 * Handling of the Json event objects streamed in response to a Kubernetes API watch request
 * Based on Akka streaming
 */
object Watch {
  
  val log = LoggerFactory.getLogger("skuber.api")

  /**
    * Get a source of events on a specific Kubernetes resource
    * @param context the applicable request context
    * @param name the name of the Kubernetes resource to watch
    * @param sinceResourceVersion if specfied all events since the resource version are included, otherwise only future events
    * @param bufSize maximum size of each event in the event stream, in bytes
    * @param format
    * @param rd
    * @tparam O
    * @return
    */
  def events[O <: ObjectResource](context: RequestContext, name: String, sinceResourceVersion: Option[String] = None, bufSize: Int)(
    implicit format: Format[O], rd: ResourceDefinition[O], lc: LoggingContext) : Future[Source[WatchEvent[O], _]] =
  {
    context.logInfo(context.logConfig.logRequestBasic, "creating watch on resource $name of kind ${rd.spec.names.kind}")

    val maybeResourceVersionQuery = sinceResourceVersion map { version => Uri.Query("resourceVersion" -> version) }
    val request = context.buildRequest(HttpMethods.GET, rd, Some(name), query = maybeResourceVersionQuery, watch = true)
    val responseFut = context.invoke(request)
    toFutureWatchEventSource(context, responseFut, bufSize)
  }

  /**
    * Get a source of events on all resources of a specified kind
    * @param context the applicable request context
    * @param sinceResourceVersion if specfied all events since the resource version are included, otherwise only future events
    * @param bufSize maximum size of each event in the event stream, in bytes
    * @param format Play json formatter for the applicable Kubernetes type, used to read each event object
    * @param rd resource definition for the kind, required for building the watch request Uri.
    * @tparam O
    * @return a Future which will eventually return a Source of events
    */
  def eventsOnKind[O <: ObjectResource](context: RequestContext, sinceResourceVersion: Option[String] = None, bufSize: Int)(
    implicit format: Format[O], rd: ResourceDefinition[O], lc: LoggingContext) : Future[Source[WatchEvent[O], _]] =
  {
    context.logInfo(context.logConfig.logRequestBasic, s"creating skuber watch on kind ${rd.spec.names.kind}")

    val maybeResourceVersionQuery = sinceResourceVersion map { v => Uri.Query("resourceVersion" -> v) }
    val request = context.buildRequest(HttpMethods.GET, rd, None, watch=true)
    val responseFut = context.invoke(request)
    toFutureWatchEventSource(context, responseFut, bufSize)
  }

  /**
    * Create a (future) Source of watch events from a (future) response to a watch request
    * @param context the applicable request context
    * @param eventStreamResponseFut the response which will contain the event stream for the source
    * @param bufSize maximum size of each event in the stream (in bytes)
    * @param format Play json formatter for the applicable Kubernetes type, used to read each event object
    * @tparam O the Kubernetes kind of each events object
    * @return a Future which will eventually return a Source of events
    */
  private def toFutureWatchEventSource[O <: ObjectResource](context: RequestContext, eventStreamResponseFut: Future[HttpResponse], bufSize: Int)(
    implicit format: Format[O],lc: LoggingContext): Future[Source[WatchEvent[O], _]] =
  {
    implicit val system = context.actorSystem
    implicit val mat = context.actorMaterializer

    eventStreamResponseFut.map { eventStreamResponse =>
      bytesSourceToWatchEventSource(eventStreamResponse.entity.dataBytes, bufSize)
    }
  }

  /**
    * Convert a source of bytes to a source of watch events of type O
    */
  private[api] def bytesSourceToWatchEventSource[O <: ObjectResource](bytesSource: Source[ByteString, _], bufSize: Int)(
    implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer, format: Format[O], lc: LoggingContext): Source[WatchEvent[O], _] =
  {
    import skuber.json.format.apiobj.watchEventFormat

    implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher

    bytesSource
        .via(JsonFraming.objectScanner(bufSize))
        .map { singleEventBytes =>
          val singleEventJson = Json.parse(singleEventBytes.utf8String)
          val validatedEvent = singleEventJson.validate[WatchEvent[O]](watchEventFormat[O])
          validatedEvent match {
            case JsSuccess(value, _) => value
            case JsError(e) => throw new K8SException(Status(message = Some("Error parsing watched object"), details = Some(s"${lc.output} - ${e.toString}")))
          }
        }
  }
}

