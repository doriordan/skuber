package skuber.api

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import akka.http.scaladsl.model.{HttpMethods, _}
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import skuber.api.client._
import skuber.{ObjectResource, ResourceDefinition}

/**
 * @author David O'Riordan
 * Handling of the Json event objects streamed in response to a Kubernetes API watch request
 * Based on Akka streaming
 */
object Watch {
  
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
    context.logInfo(context.logConfig.logRequestBasic, s"creating watch on resource $name of kind ${rd.spec.names.kind}")

    val maybeResourceVersionQuery = sinceResourceVersion map { version => Uri.Query("resourceVersion" -> version) }
    val request = context.buildRequest(HttpMethods.GET, rd, Some(name), query = maybeResourceVersionQuery, watch = true)
    val responseFut = context.invokeWatch(request)
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
    val request = context.buildRequest(HttpMethods.GET, rd, None, query = maybeResourceVersionQuery, watch = true)
    val responseFut = context.invokeWatch(request)
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
    implicit val ec: ExecutionContext = context.actorSystem.dispatcher

    eventStreamResponseFut.map { eventStreamResponse =>
      BytesToWatchEventSource(eventStreamResponse.entity.dataBytes, bufSize)
    }
  }
}

