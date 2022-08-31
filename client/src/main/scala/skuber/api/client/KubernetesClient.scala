package skuber.api.client

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import play.api.libs.json.{Format, Writes}
import skuber.api.patch.Patch
import skuber.{DeleteOptions, HasStatusSubresource, LabelSelector, ListOptions, ListResource, ObjectResource, Pod, ResourceDefinition, Scale}
import scala.concurrent.{Future, Promise}

/**
  * @author David O'Riordan
  *
  * This trait defines the skuber Kubernetes client API
  * The client API supports making requests on Kubernetes resources that are represented using the Skuber case class based data model.
  * Generally most methods target a specific namespace that has been configured for the client, but some methods can target other namespaces -
  * the descriptions and signatures of those methods should make it clear in those cases,
  * Most of the methods are typed to either a specific object resource type O or list resource type L, and require one or more of the
  * following implicit parameters, which arenormally suppied when you make some standard imports as described in the programming guide:
  * - A ResourceDefinition that supplies skuber with key details needed to make the call on the applicable resource type. These are
  * defined on the companion object of the case class that implements the resource type so generally you do not have to explicitly
  * import them.
  * - A Play JSON Format instance that knows how to marshal/unmarshal values of the applicable resource type for sending to and receiving
  * from the API server. Many of these are imported from specific json objects (e.g. skuber.json.format), but some are defined on
  * the companion object of the applicable resource type.
  * - A LoggingContext which provides additional details in the skuber logs for each call - unless overridden this will be a
  * skuber.api.client.RequestLoggingContext instance, which just adds a unique request id to the request/response logs.
  *
  * See the Skuber programming guide and examples for more information on how to use the API.
  */
trait KubernetesClient {

  /**
    * Retrieve the object resource with the specified name and type
    * @tparam O the specific object resource type e.g. Pod, Deployment
    * @param name the name of the object resource
    * @param namespace the namespace (defaults to currently configured namespace)
    * @return A future containing the retrieved resource (or an exception if resource not found)
    */
  def get[O <: ObjectResource](name: String, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O]

  /**
    * Retrieve the object resource with the specified name and type, returning None if resource does not exist
    * @tparam O the specific object resource type e.g. Pod, Deployment
    * @param name the name of the object resource
    * @param namespace the namespace (defaults to currently configured namespace)
    * @return A future containing Some(resource) if the resource is found on the cluster, or None if not found
    */
  def getOption[O <: ObjectResource](name: String, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Option[O]]

  /**
    * Retrieve the object resource with the specified name and type from the specified namespace
    * @tparam O the specific object resource type e.g. Pod, Deployment
    * @param name the name of the object resource
    * @param namespace the namespace (defaults to currently configured namespace)
    * @return A future conatining Some(resource) if the resource is found on the cluster otherwise None
    */
  @deprecated("Use 'get(name, Some(namespace))'", "2.7.6")
  def getInNamespace[O <: ObjectResource](name: String, namespace: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O]

  /**
    * Create a new object resource. If the namespace metadata field is set to a non-empty value then the object will be created
    * in that namespace, otherwise it will be created in the configured namespace of the client.
    * @param obj the resource to create on the cluster
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O the specific object resource type e.g. Pod, Deployment
    * @return A future containing the created resource returned by Kubernetes
    */
  def create[O <: ObjectResource](obj: O, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O]

  /**
    * Update an existing object resource
    * @param obj the resource with the desired updates
    * @param namespace the namespace (defaults to currently configured namespace)
    * @return A future containing the updated resource returned by Kubernetes
    */
  def update[O <: ObjectResource](obj: O, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O]

  /**
    * Delete an existing object resource
    * @param name the name of the resource to delete
    * @param gracePeriodSeconds optional parameter specifying a grace period to be applied before hard killing the resource
    * @param namespace the namespace (defaults to currently configured namespace)
    * @return A future that will be set to success if the deletion request was accepted by Kubernetes, otherwise failure
    */
  def delete[O <: ObjectResource](name: String, gracePeriodSeconds: Int = -1, namespace: Option[String] = None)(implicit rd: ResourceDefinition[O], lc: LoggingContext): Future[Unit]

  /**
    * Delete an existing object resource
    * @param name the name of the resource to delete
    * @param options contains various options that can be passed to the deletion operation, see Kubernetes documentation
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O the specific object resource type e.g. Pod, Deployment
    * @return A future that will be set to success if the deletion request was accepted by Kubernetes, otherwise failure
    */
  def deleteWithOptions[O <: ObjectResource](name: String, options: DeleteOptions, namespace: Option[String] = None)(implicit rd: ResourceDefinition[O], lc: LoggingContext): Future[Unit]

  /**
    * Delete all resources of specified type in current namespace
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam L list resource type of resources to delete e.g. PodList, DeploymentList
    * @return A future containing the list of all deleted resources
    */
  def deleteAll[L <: ListResource[_]](namespace: Option[String] = None)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  @deprecated("Use 'deleteAll()'", "2.7.6")
  def deleteAll[L <: ListResource[_]](implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Delete all resources of specified type selected by a specified label selector in current namespace
    * @param labelSelector selects the resources to delete
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam L the list resource type of resources to delete e.g. PodList, DeploymentList
    * @return A future containing the list of all deleted resources
    */
  def deleteAllSelected[L <: ListResource[_]](labelSelector: LabelSelector, namespace: Option[String] = None)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Return a list of the names of all namespaces in the cluster
    * @return a future containing the list of names of all namespaces in the cluster
    */
  def getNamespaceNames(implicit lc: LoggingContext): Future[List[String]]

  /**
    * Get list of all resources across all namespaces in the cluster of a specified list type, grouped by namespace
    * @tparam L the list resource type of resources to list e.g. PodList, DeploymentList
    * @return A future with a map containing an entry for each namespace, each entry consists of a list of resources keyed by the name of their namesapce
    */
  def listByNamespace[L <: ListResource[_]]()(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[Map[String, L]]

  /**
    * Get list of resources of a given type in a specified namespace
    * @param theNamespace the namespace to search
    * @tparam L the list resource type of the objects to retrieve e.g. PodList, DeploymentList
    * @return A future containing the resource list retrieved
    */
  @deprecated("Use 'list(Some(namespace))'", "2.7.6")
  def listInNamespace[L <: ListResource[_]](theNamespace: String)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Get list of all resources of specified type in the configured namespace for the client
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam L the list type to retrieve e.g. PodList, DeploymentList
    * @return A future containing the resource list retrieved
    */
  def list[L <: ListResource[_]](namespace: Option[String] = None)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  @deprecated("Use 'list()'", "2.7.6")
  def list[L <: ListResource[_]](implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]


  /**
    * Get list of selected resources of specified type in the configured namespace for the client
    * @param labelSelector the label selector to use to select the resources to return
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam L the list type of the resources to retrieve e.g. PodList, DeploymentList
    * @return A future containing the resource list retrieved
    */
  def listSelected[L <: ListResource[_]](labelSelector: LabelSelector, namespace: Option[String] = None)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Get list of resources of specified type, applying the specified options to the list request
    * @param options a set of options to be added to the request that can modify how the request is handled by Kubernetes.
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam L the list type of the resources to retrieve e.g. PodList, DeploymentList
    * @return A future containing the resource list retrieved
    */
  def listWithOptions[L <: ListResource[_]](options: ListOptions, namespace: Option[String] = None)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Update the status subresource of a given object resource. Only supported by certain object resource kinds (which need to have defined an
    * implicit HasStatusResource)
    * This method is generally for advanced use cases such as custom controllers
    * @param statusEv this implicit provides evidence that the resource kind has status subresources, so supports this method
    * @param obj the name of the object resource whose status subresource is to be updated
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O The resource type
    * @return A future containing the full updated object resource
    */
  def updateStatus[O <: ObjectResource](obj: O, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O],statusEv: HasStatusSubresource[O], lc: LoggingContext): Future[O]

  /**
    * Get the status subresource of a given object resource. Only supported by certain object resource kinds (which need to have defined an
    * implicit HasStatusResource)
    * This method is generally for advanced use cases such as custom controllers.
    * @param name the name of the object resource
    * @param statusEv this implicit provides evidence that the resource kind has status subresources, so supports this method
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O the resource type e.g. Pod, Deployment
    * @return A future containing the object resource including current status
    */
  def getStatus[O <: ObjectResource](name: String, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O],statusEv: HasStatusSubresource[O], lc: LoggingContext): Future[O]

  /**
    * Place a watch on a specific object - this returns a source of events that will be produced whenever the object is added, modified or deleted
    * on the cluster
    * Note: Most applications should probably use watchContinuously instead, which transparently reconnects and continues the watch in the case of server
    * timeouts - the source returned by this method will complete in the presence of such timeouts or other disconnections.
    * @param obj the name of the object to watch
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O the type of the object to watch e.g. Pod, Deployment
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watch[O <: ObjectResource](obj: O, namespace: Option[String])(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]]

  @deprecated("Use 'watch(obj, None)'", "2.7.6")
  def watch[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]]

  /**
    * Place a watch for any changes to a specific object, optionally since a given resource version - this returns a source of events that will be produced
    * whenever the object is modified or deleted on the cluster, if the resource version on the updated object is greater than or equal to that specified.
    * Note: Most applications should probably use watchContinuously instead, which transparently reconnects and continues the watch in the case of server
    * timeouts - the source returned by this method will complete in the presence of such timeouts or other disconnections.
    * @param name the name of the object
    * @param sinceResourceVersion the resource version - normally the applications gets the current resource from the metadata of a list call on the
    * applicable type (e.g. PodList, DeploymentList) and then supplies that to this method. If no resource version is specified, a single ADDED event will
    * be produced for an already existing object followed by events for any future changes.
    * @param bufSize An optional buffer size for the returned on-the-wire representation of each modified object - normally the default is more than enough.
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O the type of the resource to watch
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watch[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]]

  /**
    * Place a watch on changes to all objects of a specific resource type - this returns a source of events that will be produced whenever an object
    * of the specified type is added, modified or deleted on the cluster
    * Note: Most applications should probably use watchAllContinuously instead, which transparently reconnects and continues the watch in the case of server
    * timeouts - the source returned by this method will complete in the presence of such timeouts or other disconnections.
    *
    * @param sinceResourceVersion the resource version - normally the applications gets the current resource from the metadata of a list call on the
    * applicable type (e.g. PodList, DeploymentList) and then supplies that to this method. If no resource version is specified, a single ADDED event will
    * be produced for an already existing object followed by events for any future changes.
    * @param bufSize optional buffer size for each modified object received, normally the default is more than enough
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O the type of resource to watch e.g. Pod, Dpeloyment
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watchAll[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]]

  /**
    * Watch a specific object resource continuously. This returns a source that will continue to produce
    * events on any updates to the object even if the server times out, by transparently restarting the watch as needed.
    * @param obj  the object resource to watch
    * @tparam O the type of the resource e.g Pod
    * @param namespace the namespace (defaults to currently configured namespace)
    * @return  A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watchContinuously[O <: ObjectResource](obj: O, namespace: Option[String])(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

  @deprecated("Use watchContinuously(obj, namespace = None)", "2.7.6")
  def watchContinuously[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

  /**
    * Watch a specific object resource continuously. This returns a source that will continue to produce
    * events on any updates to the object even if the server times out, by transparently restarting the watch as needed.
    * The optional resourceVersion can be used to specify that only events on versions of the object greater than or equal to
    * the resource version should be produced.
    *
    * @param name the name of the resource to watch
    * @param sinceResourceVersion the resource version - normally the applications gets the current resource version from the metadata of a list call on the
    * applicable type (e.g. PodList, DeploymentList) and then supplies that to this method to receive any future updates. If no resource version is specified,
    * a single ADDED event will be produced for an already existing object followed by events for any future changes.
    * @param bufSize optional buffer size for received object updates, normally the default is more than enough
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O the type of the resource
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watchContinuously[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

  /**
    * Watch all object resources of a specified type continuously. This returns a source that will continue to produce
    * events even if the server times out, by transparently restarting the watch as needed.
    * The optional resourceVersion can be used to specify that only events on versions of objects greater than or equal to
    * the resource version should be produced.
    *
    * @param sinceResourceVersion the resource version - normally the applications gets the current resource version from the metadata of a list call on the
    * applicable type (e.g. PodList, DeploymentList) and then supplies that to this method to receive any future updates. If no resource version is specified,
    * a single ADDED event will be produced for an already existing object followed by events for any future changes.
    * @param bufSize optional buffer size for received object updates, normally the default is more than enough
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O the type pf the resource
    * @return A future containing an Akka streams Source of WatchEvents that will be emitted
    */
  def watchAllContinuously[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

/**
  * Watch all object resources of a specified type continuously, passing the specified options to the API server with the watch request.
  * This returns a source that will continue to produce events even if the server times out, by transparently restarting the watch as needed.
  * @param options a set of list options to pass to the server. See https://godoc.org/k8s.io/apimachinery/pkg/apis/meta/v1#ListOptions
  * for the meaning of the options. Note that the `watch` flag in the options will be ignored / overridden by the client, which
  * ensures a watch is always requested on the server.
  * @param bufsize optional buffer size for received object updates, normally the default is more than enough
  * @param namespace the namespace (defaults to currently configured namespace)
  * @tparam O the resource type to watch
  * @return A future containing an Akka streams Source of WatchEvents that will be emitted
  */
  def watchWithOptions[O <: ObjectResource](options: ListOptions, bufsize: Int = 10000, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

  /**
   * Get the scale subresource of the named object resource
   * This can only be called on certain resource types that support scale subresources.
   * Normally used in advanced use cases such as custom controllers
   * @param objName the name of the resource
   * @param namespace the namespace (defaults to currently configured namespace)
   * @param sc this implicit parameter provides evidence that the resource type supports scale subresources. Normally defined in the companion
   * object of the resource type if applicable so does not need to be imported.
   * @tparam O the type of the resource e.g. Pod
   * @return a future containing the scale subresource
   */
  def getScale[O <: ObjectResource](objName: String, namespace: Option[String] = None)(implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc: LoggingContext) : Future[Scale]

  /**
    * Update the scale subresource of a specified resource
    * This can only be called on certain resource types that support scale subresources.
    * Normally used in advanced use cases such as custom controllers
    *
    * @param objName the name of the resource
    * @param scale the updated scale to set on the resource
   * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O the type of the resource
    * @param sc this implicit parameter provides evidence that the resource type supports scale subresources. Normally defined in the companion
    * object of the resource type if applicable so does not need to be imported
    * @return a future containing the successfully updated scale subresource
    */
  def updateScale[O <: ObjectResource](objName: String, scale: Scale, namespace: Option[String] = None)(implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc: LoggingContext): Future[Scale]

  @deprecated("use getScale followed by updateScale instead")
  def scale[O <: ObjectResource](objName: String, count: Int, namespace: Option[String] = None)(implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc: LoggingContext): Future[Scale]

  /**
    * Patch a resource
    * @param name The name of the resource to patch
    * @param patchData The patch data to apply to the resource
    * @param namespace the namespace (defaults to currently configured namespace)
    * @param patchfmt an implicit parameter that knows how to serialise the patch data to Json
    * @tparam P the patch type (specifies the patch strategy details)
    * @tparam O the type of the resource to be patched
    * @return a future conating the patched resource
    */
  def patch[P <: Patch, O <: ObjectResource](name: String, patchData: P, namespace: Option[String] = None)
      (implicit patchfmt: Writes[P], fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext = RequestLoggingContext()): Future[O]

  /**
    * Apply a patch to a specified object resource using json merge patch strategy
    * @param obj the name of the resource to patch
    * @param patch the patch (in json patch format)
    * @param namespace the namespace (defaults to currently configured namespace)
    * @tparam O the type of the resource
    * @return A future containing the patched resource
    */
  @deprecated("use patch instead","v2.1")
  def jsonMergePatch[O <: ObjectResource](obj: O, patch: String, namespace: Option[String] = None)(implicit rd: ResourceDefinition[O], fmt: Format[O], lc: LoggingContext): Future[O]

  /**
    * Get the logs from a pod (similar to `kubectl logs ...`). The logs are streamed using an Akka streams source
    * @param name the name of the pod
    * @param queryParams optional parameters of the request (for example container name)
    * @param namespace if set this specifies the namespace of the pod (otherwise the configured namespace is used)
    * @return A future containing a Source for the logs stream.
    */
  def getPodLogSource(name: String, queryParams: Pod.LogQueryParams, namespace: Option[String] = None)(implicit lc: LoggingContext): Future[Source[ByteString, _]]

  /**
    * Execute a command in a pod (similar to `kubectl exec ...`)
    * @param podName the name of the pod
    * @param command the command to execute
    * @param maybeContainerName an optional container name
    * @param maybeStdin optional Akka Source for sending input to stdin for the command
    * @param maybeStdout optional Akka Sink to receive output from stdout for the command
    * @param maybeStderr optional Akka Sink to receive output from stderr for the command
    * @param tty optionally set tty on
    * @param maybeClose if set, this can be used to close the connection to the pod by completing the promise
    * @param namespace if set this specifies the namespace of the pod (otherwise the configured namespace is used)
    * @return A future indicating the exec command has been submitted
    */
  def exec(podName: String,
    command: Seq[String],
    maybeContainerName: Option[String] = None,
    maybeStdin: Option[Source[String, _]] = None,
    maybeStdout: Option[Sink[String, _]] = None,
    maybeStderr: Option[Sink[String, _]] = None,
    tty: Boolean = false,
    maybeClose: Option[Promise[Unit]] = None,
    namespace: Option[String] = None)(implicit lc: LoggingContext): Future[Unit]

  /**
    * Return list of API versions supported by the server
    * @param lc
    * @return a future containing the list of API versions
    */
  def getServerAPIVersions(implicit lc: LoggingContext): Future[List[String]]

  /**
    * Create a new KubernetesClient instance that reuses this clients configuration and connection resources, but with a different
    * target namespace.
    * This is useful for applications that need a lightweight way to target multiple or dynamic namespaces.
    * @param newNamespace
    * @return
    */
  @deprecated("All other api endpoints include namespace parameter", "2.7.6")
  def usingNamespace(newNamespace: String): KubernetesClient

  /**
    * Closes the client. Any requests to the client after this is called will be rejected.
    */
  def close: Unit

  // Some parameters of the client that it may be useful for some applications to read
  val logConfig: LoggingConfig // the logging configuration for client requests
  val clusterServer: String // the URL of the target Kubernetes API server
  val namespaceName: String // the name of the configured namespace for this client
}
