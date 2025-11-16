package skuber.api.client

import play.api.libs.json.Format
import skuber.model.{LabelSelector, ListResource, ObjectResource, ResourceDefinition}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Watcher[O <: ObjectResource] {

  type EventSource // the type of the source of watch events returned by watch requests: override in concrete implementation

  /**
    * The fundamental watch operation which underpins all other watch operations on this API, so the concrete watcher implementations
    * of this trait only need to implement this method.
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
  def watchWithParameters(parameters: WatchParameters)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource =
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
  // See https://kubernetes.io/docs/reference/using-api/api-concepts/#semantics-for-watch for the resource version
  // related semantics of watch:
  // - Watching from most recent version (resource version unset)
  // - Watching From Any version (resource version set to zero)
  // - Watching From specific Resource Version (non-zero resource version set)

  /**
    * Watch all objects of type O in current namespace, starting from most recent version
    *
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O
    * @return
    */
  def watch()(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource = {
    watchWithParameters(WatchParameters())
  }

  /**
    * Watch events on all resources of type O in current namespace, outputting all events that occur since the specified version of
    * the resource collection (or any version if set to "0")
    *
    * @param resourceVersion he initial version of the resource collection from which events should be output
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O
    * @return an Akka source of events
    */
  def watchStartingFromVersion(resourceVersion: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource = {
    val watchParams = WatchParameters(resourceVersion = Some(resourceVersion))
    watchWithParameters(watchParams)
  }

  /**
    * Watch all events on all objects of type O across whole cluster, outputting all events since
    * the specified resource version (or any version if set to "0")
    *
    * @param resourceVersion the initial version of the resource collection from which events should be output
    * @param bufSize
    * @param errorHandler
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O
    * @return an Akka source of events
    */
  def watchClusterStartingFromVersion(resourceVersion: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource =
  {
    val watchParameters = WatchParameters(resourceVersion = Some(resourceVersion), clusterScope = true)
    watchWithParameters(watchParameters)
  }

  /**
    * Watch events on all resources of type O across whole cluster, starting from most recent version
    *
    * @param bufSize
    * @param errorHandler
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O
    * @return an Akka source of events
    */
  def watchCluster()(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource =
  {
    val watchParameters = WatchParameters(clusterScope = true)
    watchWithParameters(watchParameters)
  }

  /**
    * Watch a single object resource of the specified name in current namespace, starting from most recent version
    *
    * @param name the name of the object to watch
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O the kind of the object to watch
    * @return a source of events from the object
    */
  def watchObject(name: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource =
  {
    val watchParameters = WatchParameters(fieldSelector = Some(s"metadata.name=$name"))
    watchWithParameters(watchParameters)
  }

  /**
    * Watch the named object resource in the current namespace, outputting all events from the specified resource version
    * (or any version if set to "0")
    *
    * @param name the name of the object to watch
    * @param resourceVersion the initial version of the resource from which events should be output
    * @param fmt
    * @param rd
    * @param lc
    * @tparam O the kind of the object to watch
    * @return a source of events from the object
    */
  def watchObjectStartingFromVersion(name: String, resourceVersion: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): EventSource = {
    val watchParameters = WatchParameters(resourceVersion = Some(resourceVersion), fieldSelector = Some(s"metadata.name=$name"))
    watchWithParameters(watchParameters)
  }
}
