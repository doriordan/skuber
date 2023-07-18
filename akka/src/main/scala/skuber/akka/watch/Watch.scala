package skuber.akka.watch

import skuber.model.{ObjectResource, ResourceDefinition, LoggingContext}
import play.api.libs.json._

trait Watch {
  /**
    * Place a watch on a specific object - this returns a source of events that will be produced whenever the object is added, modified or deleted
    * on the cluster
    * Note: Most applications should probably use watchContinuously instead, which transparently reconnects and continues the watch in the case of server
    * timeouts - the source returned by this method will complete in the presence of such timeouts or other disconnections.
    *
    * @param obj the name of the object to watch
    * @tparam O the type of the object to watch e.g. Pod, Deployment
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watch[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]]

  /**
    * Place a watch for any changes to a specific object, optionally since a given resource version - this returns a source of events that will be produced
    * whenever the object is modified or deleted on the cluster, if the resource version on the updated object is greater than or equal to that specified.
    * Note: Most applications should probably use watchContinuously instead, which transparently reconnects and continues the watch in the case of server
    * timeouts - the source returned by this method will complete in the presence of such timeouts or other disconnections.
    *
    * @param name                 the name of the object
    * @param sinceResourceVersion the resource version - normally the applications gets the current resource from the metadata of a list call on the
    *                             applicable type (e.g. PodList, DeploymentList) and then supplies that to this method. If no resource version is specified, a single ADDED event will
    *                             be produced for an already existing object followed by events for any future changes.
    * @param bufSize              An optional buffer size for the returned on-the-wire representation of each modified object - normally the default is more than enough.
    * @tparam O the type of the resource to watch
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watch[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]]

  /**
    * Place a watch on changes to all objects of a specific resource type - this returns a source of events that will be produced whenever an object
    * of the specified type is added, modified or deleted on the cluster
    * Note: Most applications should probably use watchAllContinuously instead, which transparently reconnects and continues the watch in the case of server
    * timeouts - the source returned by this method will complete in the presence of such timeouts or other disconnections.
    *
    * @param sinceResourceVersion the resource version - normally the applications gets the current resource from the metadata of a list call on the
    *                             applicable type (e.g. PodList, DeploymentList) and then supplies that to this method. If no resource version is specified, a single ADDED event will
    *                             be produced for an already existing object followed by events for any future changes.
    * @param bufSize              optional buffer size for each modified object received, normally the default is more than enough
    * @tparam O the type of resource to watch e.g. Pod, Dpeloyment
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watchAll[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]]

  /**
    * Watch a specific object resource continuously. This returns a source that will continue to produce
    * events on any updates to the object even if the server times out, by transparently restarting the watch as needed.
    *
    * @param obj the object resource to watch
    * @tparam O the type of the resource e.g Pod
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watchContinuously[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

  /**
    * Watch a specific object resource continuously. This returns a source that will continue to produce
    * events on any updates to the object even if the server times out, by transparently restarting the watch as needed.
    * The optional resourceVersion can be used to specify that only events on versions of the object greater than or equal to
    * the resource version should be produced.
    *
    * @param name                 the name of the resource to watch
    * @param sinceResourceVersion the resource version - normally the applications gets the current resource version from the metadata of a list call on the
    *                             applicable type (e.g. PodList, DeploymentList) and then supplies that to this method to receive any future updates. If no resource version is specified,
    *                             a single ADDED event will be produced for an already existing object followed by events for any future changes.
    * @param bufSize              optional buffer size for received object updates, normally the default is more than enough
    * @param errorHandler         an optional function that takes a single string parameter - it will be invoked with the error details whenever ERROR events are received
    * @tparam O the type of the resource
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watchContinuously[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, errorHandler: Option[String => _] = None)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

  /**
    * Watch all object resources of a specified type continuously. This returns a source that will continue to produce
    * events even if the server times out, by transparently restarting the watch as needed.
    * The optional resourceVersion can be used to specify that only events on versions of objects greater than or equal to
    * the resource version should be produced.
    *
    * @param sinceResourceVersion the resource version - normally the applications gets the current resource version from the metadata of a list call on the
    *                             applicable type (e.g. PodList, DeploymentList) and then supplies that to this method to receive any future updates. If no resource version is specified,
    *                             a single ADDED event will be produced for an already existing object followed by events for any future changes.
    * @param bufSize              optional buffer size for received object updates, normally the default is more than enough
    * @param errorHandler         an optional function that takes a single string parameter - it will be invoked with the error details whenever ERROR events are received
    * @tparam O the type pf the resource
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watchAllContinuously[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, errorHandler: Option[String => _] = None)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

  /**
    * Watch all object resources of a specified type continuously, passing the specified options to the API server with the watch request.
    * This returns a source that will continue to produce events even if the server times out, by transparently restarting the watch as needed.
    *
    * @param options      a set of list options to pass to the server. See https://godoc.org/k8s.io/apimachinery/pkg/apis/meta/v1#ListOptions
    *                     for the meaning of the options. Note that the `watch` flag in the options will be ignored / overridden by the client, which
    *                     ensures a watch is always requested on the server.
    * @param bufsize      optional buffer size for received object updates, normally the default is more than enough
    * @param errorHandler an optional function that takes a single string parameter - it will be invoked with the error details whenever ERROR events are received
    * @tparam O the resource type to watch
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watchWithOptions[O <: ObjectResource](options: ListOptions, bufsize: Int = 10000, errorHandler: Option[String => _] = None)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]
}
