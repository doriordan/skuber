package skuber.akkaclient.watch

import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import skuber.akkaclient.impl.AkkaKubernetesClientImpl
import skuber.api.client._
import skuber.model.{ListOptions, ObjectResource, ResourceDefinition}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
 * @author David O'Riordan
 * Handling of the Json event objects streamed in response to a Kubernetes API watch request
 * Based on Akka streaming
 */
object Watch {

  /**
    * Get a source of events on a specific Kubernetes resource type
    * @param context the applicable request context
    * @param name the name of the Kubernetes resource to watch
    * @param sinceResourceVersion if specfied all events since the resource version are included, otherwise only future events
    * @param bufSize maximum size of each event in the event stream, in bytes
    * @param format the json formatter for the object resource type
    * @param rd the resource definition for the object resource type
    * @tparam O the object resource type
    * @return
    */
  def events[O <: ObjectResource](context: AkkaKubernetesClientImpl, name: String, sinceResourceVersion: Option[String] = None, bufSize: Int, errorHandler: Option[String => _])(
    implicit format: Format[O], rd: ResourceDefinition[O], lc: LoggingContext) : Future[Source[WatchEvent[O], _]] =
  {
    context.logInfo(context.logConfig.logRequestBasic, s"creating watch on resource $name of kind ${rd.spec.names.kind}")

    val nameFieldSelector=Some(s"metadata.name=$name")
    val watchOptions=ListOptions(
      resourceVersion = sinceResourceVersion,
      watch = Some(true),
      fieldSelector = nameFieldSelector
    )
    val request = context.buildRequest(HttpMethods.GET, rd, None, query = Some(Uri.Query(watchOptions.asMap)))
    val responseFut = context.invokeWatch(request)
    toFutureWatchEventSource(context, responseFut, bufSize, errorHandler)
  }

  /**
    * Get a source of events on all resources of a specified object resource type
    * @param context the applicable request context
    * @param sinceResourceVersion if specfied all events since the resource version are included, otherwise only future events
    * @param bufSize maximum size of each event in the event stream, in bytes
    * @param format Play json formatter for the applicable Kubernetes type, used to read each event object
    * @param rd resource definition for the kind, required for building the watch request Uri.
    * @tparam O the object resource type
    * @return a Future which will eventually return a Source of events
    */
  def eventsOnKind[O <: ObjectResource](context: AkkaKubernetesClientImpl, sinceResourceVersion: Option[String] = None, bufSize: Int, errorHandler: Option[String => _])(
    implicit format: Format[O], rd: ResourceDefinition[O], lc: LoggingContext) : Future[Source[WatchEvent[O], _]] =
  {
    context.logInfo(context.logConfig.logRequestBasic, s"creating skuber watch on kind ${rd.spec.names.kind}")

    val watchOptions=ListOptions(resourceVersion = sinceResourceVersion, watch = Some(true))
    val request = context.buildRequest(HttpMethods.GET, rd, None, query = Some(Uri.Query(watchOptions.asMap)))
    val responseFut = context.invokeWatch(request)
    toFutureWatchEventSource(context, responseFut, bufSize, errorHandler)
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
  private def toFutureWatchEventSource[O <: ObjectResource](context: AkkaKubernetesClientImpl, eventStreamResponseFut: Future[HttpResponse], bufSize: Int, errorHandler: Option[String => _])(
    implicit format: Format[O],lc: LoggingContext): Future[Source[WatchEvent[O], _]] =
  {
    implicit val ec: ExecutionContext = context.actorSystem.dispatcher

    eventStreamResponseFut.map { eventStreamResponse =>
      BytesToWatchEventSource(context, eventStreamResponse.entity.dataBytes, bufSize, errorHandler)
    }
  }
}

