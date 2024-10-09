package skuber.api.client

import play.api.libs.json.Format
import skuber.model.{LabelSelector, ListResource, ObjectResource, ResourceDefinition}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Watcher[O <: ObjectResource] {

  type EventSource // the type of the source of watch events returned by watch requests: override in concrete implementation

  /**
    * The fundamental watch operation which underpins all other watch operations on this API.
    *
    * @param watchRequestOptions
    * @param clusterScope
    * @param bufSize
    * @param errorHandler
    * @param fmt
    * @param rd
    * @param lc
    * @return source of events streamed to the client as a result of the watch request
    */
  protected def _watch(watchRequestOptions: ListOptions, clusterScope: Boolean, bufSize: Int, errorHandler: Option[String => _])(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource

  val defaultWatchRequestTimeoutSeconds: Long
  val defaultBufSize: Int

  final case class WatchParameters(
    clusterScope: Boolean = false, // whether to watch objects across whose cluster or in current namespace only
    resourceVersion: Option[String] = None, // the resource version after which events should be watched, normally this is returned by a prior list operation or set to None for all events
    labelSelector: Option[LabelSelector] = None, // select objects to watch by labels - see Kubernetes docs for details
    fieldSelector: Option[String] = None, // select objects to watch by field - see Kubernetes docs for details
    bufSize: Int = defaultBufSize, // size of buffer into which each event is read, increase if not big enough
    errorHandler: Option[String => _] = None, // if errors are encountered when streaming the events invoke this handler
    // the timeout of the request sent to the API server to perform the watch
    // after each watch request times out, skuber transparently resends the request and in this way continues watching for an unlimited period
    // for this work it is important that the request timeout is less than the connection idle timeout (which has a default of 1 minute)
    timeoutSeconds: Option[Long] = Some(defaultWatchRequestTimeoutSeconds)
  )

  /**
    * Generic watch command on one or more objects of type O. The actual objects watched
    * and other key watch parameters will be determined by the parameters, see above for summary of each field.
    *
    * @param watchParameters the options passed in the watch request to the Kubernetes API
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O the kind of object resource to watch
    * @return a source of events from the objects in scope for the watch request
    */
  def watch(parameters: WatchParameters)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource =
  {
    val options = ListOptions(
      resourceVersion = parameters.resourceVersion,
      labelSelector = parameters.labelSelector,
      timeoutSeconds = parameters.timeoutSeconds,
      fieldSelector = parameters.fieldSelector
    )
    _watch(options, parameters.clusterScope, parameters.bufSize, parameters.errorHandler)
  }

  // Utility watch operations for common use cases

  /**
    * Watch all objects of type O in current namespace, streaming all events from the beginning of their history in the cluster
    *
    * @param parameters
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O
    * @return
    */
  def watchSinceBeginning()(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource = {
    watch(WatchParameters())
  }

  /**
    * Watch all events on all objects of type O in current namespace since the specified resource version, which is usually returned by a
    * prior list operation
    *
    * @param sinceResourceVersion
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O
    * @return an Akka source of events
    */
  def watchSinceVersion(sinceResourceVersion: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource = {
    val watchParams = WatchParameters(resourceVersion = Some(sinceResourceVersion))
    watch(watchParams)
  }

  /**
    * Watch all events on all objects of type O across whole cluster since a specific resource version
    * (usually returned by a prior list operation)
    *
    * @param version the resource version after which events will be returned
    * @param bufSize
    * @param errorHandler
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O
    * @return an Akka source of events
    */
  def watchClusterSinceVersion(sinceResourceVersion: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource =
  {
    val watchParameters = WatchParameters(resourceVersion = Some(sinceResourceVersion), clusterScope = true)
    watch(watchParameters)
  }

  /**
    * Watch all events on all objects of type O across whole cluster since the beginning (first stored event on the cluster)
    *
    * @param bufSize
    * @param errorHandler
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O
    * @return an Akka source of events
    */
  def watchClusterSinceBeginning()(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource =
  {
    val watchParameters = WatchParameters(clusterScope = true)
    watch(watchParameters)
  }

  /**
    * Watch a single object resource of the specified name in current namespace since its creation
    *
    * @param name the name of the object to watch
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O the kind of the object to watch
    * @return a source of events from the object
    */
  def watchObjectSinceBeginning(name: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource =
  {
    val watchParameters = WatchParameters(fieldSelector = Some(s"metadata.name=$name"))
    watch(watchParameters)
  }

  /**
    * Watch a single object resource of the specified name in current namespace since its creation
    *
    * @param name the name of the object to watch
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O the kind of the object to watch
    * @return a source of events from the object
    */
  def watchObjectSinceVersion(name: String, sinceResourceVersion: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource = {
    val watchParameters = WatchParameters(resourceVersion = Some(sinceResourceVersion), fieldSelector = Some(s"metadata.name=$name"))
    watch(watchParameters)
  }

  def listWatch(lister: ListOptions => Future[ListResource[O]])(watchParameters: WatchParameters)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[EventSource] =
  {
    val listOptions = ListOptions(
      fieldSelector = watchParameters.fieldSelector,
      labelSelector = watchParameters.labelSelector,
      resourceVersion = watchParameters.resourceVersion
    )
    lister(listOptions).map { list =>
      watch(watchParameters.copy(resourceVersion = Some(list.resourceVersion)))
    }
  }
}
