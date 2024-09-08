package skuber.api.client

import play.api.libs.json.{Writes,Format}
import skuber.model.{HasStatusSubresource, LabelSelector, ListResource, ObjectResource}
import skuber.api.patch.Patch
import skuber.model.{ResourceDefinition, Scale}

import scala.concurrent.Future

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
    *
    * @tparam O the specific object resource type e.g. Pod, Deployment
    * @param name the name of the object resource
    * @return A future containing the retrieved resource (or an exception if resource not found)
    */
  def get[O <: ObjectResource](name: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O]

  /**
    * Retrieve the object resource with the specified name and type, returning None if resource does not exist
    *
    * @tparam O the specific object resource type e.g. Pod, Deployment
    * @param name the name of the object resource
    * @return A future containing Some(resource) if the resource is found on the cluster, or None if not found
    */
  def getOption[O <: ObjectResource](name: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Option[O]]

  /**
    * Retrieve the object resource with the specified name and type from the specified namespace
    *
    * @tparam O the specific object resource type e.g. Pod, Deployment
    * @param name      the name of the object resource
    * @param namespace the namespace containing the object resource
    * @return A future containing Some(resource) if the resource is found on the cluster otherwise None
    */
  def getInNamespace[O <: ObjectResource](name: String, namespace: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O]

  /**
    * Create a new object resource. If the namespace metadata field is set to a non-empty value then the object will be created
    * in that namespace, otherwise it will be created in the configured namespace of the client.
    *
    * @param obj the resource to create on the cluster
    * @tparam O the specific object resource type e.g. Pod, Deployment
    * @return A future containing the created resource returned by Kubernetes
    */
  def create[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O]

  /**
    * Update an existing object resource
    *
    * @param obj the resource with the desired updates
    * @return A future containing the updated resource returned by Kubernetes
    */
  def update[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O]

  /**
    * Delete an existing object resource
    *
    * @param name               the name of the resource to delete
    * @param gracePeriodSeconds optional parameter specifying a grace period to be applied before hard killing the resource
    * @return A future that will be set to success if the deletion request was accepted by Kubernetes, otherwise failure
    */
  def delete[O <: ObjectResource](name: String, gracePeriodSeconds: Int = -1)(implicit rd: ResourceDefinition[O], lc: LoggingContext): Future[Unit]

  /**
    * Delete an existing object resource
    *
    * @param name    the name of the resource to delete
    * @param options contains various options that can be passed to the deletion operation, see Kubernetes documentation
    * @tparam O the specific object resource type e.g. Pod, Deployment
    * @return A future that will be set to success if the deletion request was accepted by Kubernetes, otherwise failure
    */
  def deleteWithOptions[O <: ObjectResource](name: String, options: DeleteOptions)(implicit rd: ResourceDefinition[O], lc: LoggingContext): Future[Unit]

  /**
    * Delete all resources of specified type in current namespace
    *
    * @tparam L list resource type of resources to delete e.g. PodList, DeploymentList
    * @return A future containing the list of all deleted resources
    */
  def deleteAll[L <: ListResource[_]]()(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Delete all resources of specified type selected by a specified label selector in current namespace
    *
    * @param labelSelector selects the resources to delete
    * @tparam L the list resource type of resources to delete e.g. PodList, DeploymentList
    * @return A future containing the list of all deleted resources
    */
  def deleteAllSelected[L <: ListResource[_]](labelSelector: LabelSelector)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Return a list of the names of all namespaces in the cluster
    *
    * @return a future containing the list of names of all namespaces in the cluster
    */
  def getNamespaceNames(implicit lc: LoggingContext): Future[List[String]]

  /**
    * Get list of all resources across all namespaces in the cluster of a specified list type, grouped by namespace
    *
    * @tparam L the list resource type of resources to list e.g. PodList, DeploymentList
    * @return A future with a map containing an entry for each namespace, each entry consists of a list of resources keyed by the name of their namesapce
    */
  def listByNamespace[L <: ListResource[_]]()(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[Map[String, L]]

  /**
    * Get list of resources of a given type in the whole cluster
    */
  def listInCluster[L <: ListResource[_]](options: Option[ListOptions])(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Get list of resources of a given type in a specified namespace
    *
    * @param theNamespace the namespace to search
    * @tparam L the list resource type of the objects to retrieve e.g. PodList, DeploymentList
    * @return A future containing the resource list retrieved
    */
  def listInNamespace[L <: ListResource[_]](theNamespace: String)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Get list of all resources of specified type in the configured namespace for the client
    *
    * @tparam L the list type to retrieve e.g. PodList, DeploymentList
    * @return A future containing the resource list retrieved
    */
  def list[L <: ListResource[_]]()(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Get list of selected resources of specified type in the configured namespace for the client
    *
    * @param labelSelector the label selector to use to select the resources to return
    * @tparam L the list type of the resources to retrieve e.g. PodList, DeploymentList
    * @return A future containing the resource list retrieved
    */
  def listSelected[L <: ListResource[_]](labelSelector: LabelSelector)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Get list of resources of specified type, applying the specified options to the list request
    *
    * @param options a set of options to be added to the request that can modify how the request is handled by Kubernetes.
    * @tparam L the list type of the resources to retrieve e.g. PodList, DeploymentList
    * @return A future containing the resource list retrieved
    */
  def listWithOptions[L <: ListResource[_]](options: ListOptions)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L]

  /**
    * Update the status subresource of a given object resource. Only supported by certain object resource kinds (which need to have defined an
    * implicit HasStatusResource)
    * This method is generally for advanced use cases such as custom controllers
    *
    * @param statusEv this implicit provides evidence that the resource kind has status subresources, so supports this method
    * @param obj      the name of the object resource whose status subresource is to be updated
    * @tparam O The resource type
    * @return A future containing the full updated object resource
    */
  def updateStatus[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O], statusEv: HasStatusSubresource[O], lc: LoggingContext): Future[O]

  /**
    * Get the status subresource of a given object resource. Only supported by certain object resource kinds (which need to have defined an
    * implicit HasStatusResource)
    * This method is generally for advanced use cases such as custom controllers.
    *
    * @param name     the name of the object resource
    * @param statusEv this implicit provides evidence that the resource kind has status subresources, so supports this method
    * @tparam O the resource type e.g. Pod, Deployment
    * @return A future containing the object resource including current status
    */
  def getStatus[O <: ObjectResource](name: String)(implicit fmt: Format[O], rd: ResourceDefinition[O], statusEv: HasStatusSubresource[O], lc: LoggingContext): Future[O]

  /**
   * Get the scale subresource of the named object resource
   * This can only be called on certain resource types that support scale subresources.
   * Normally used in advanced use cases such as custom controllers
   * @param objName the name of the resource
   * @param sc this implicit parameter provides evidence that the resource type supports scale subresources. Normally defined in the companion
   * object of the resource type if applicable so does not need to be imported.
   * @tparam O the type of the resource e.g. Pod
   * @return a future containing the scale subresource
   */
  def getScale[O <: ObjectResource](objName: String)(implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc: LoggingContext) : Future[Scale]

  /**
    * Update the scale subresource of a specified resource
    * This can only be called on certain resource types that support scale subresources.
    * Normally used in advanced use cases such as custom controllers
    *
    * @param objName the name of the resource
    * @param scale the updated scale to set on the resource
    * @tparam O the type of the resource
    * @param sc this implicit parameter provides evidence that the resource type supports scale subresources. Normally defined in the companion
    * object of the resource type if applicable so does not need to be imported
    * @return a future containing the successfully updated scale subresource
    */
  def updateScale[O <: ObjectResource](objName: String, scale: Scale)(implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc: LoggingContext): Future[Scale]

  /**
    * Patch a resource
    * @param name The name of the resource to patch
    * @param patchData The patch data to apply to the resource
    * @param namespace the namespace (defaults to currently configured namespace)
    * @param patchfmt an implicit parameter that knows how to serialise the patch data to Json
    * @tparam P the patch type (specifies the patch strategy details)
    * @tparam O the type of the resource to be patched
    * @return a future containing the patched resource
    */
  def patch[P <: Patch, O <: ObjectResource](name: String, patchData: P, namespace: Option[String] = None)
      (implicit patchfmt: Writes[P], fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext = RequestLoggingContext()): Future[O]

  /**
    * Return list of API versions supported by the server
    * @param lc logging context
    * @return a future containing the list of API versions
    */
  def getServerAPIVersions(implicit lc: LoggingContext): Future[List[String]]

  /**
    * Create a new KubernetesClient instance that reuses this clients configuration and connection resources, but with a different
    * target namespace.
    * This is useful for applications that need a lightweight way to target multiple or dynamic namespaces.
    * @param newNamespace target namespace
    * @return the new client instance
    */
  def usingNamespace(newNamespace: String): KubernetesClient

  /**
    * Closes the client. Any requests to the client after this is called will be rejected.
    */
  def close(): Unit

  // Some parameters of the client that it may be useful for some applications to read
  val logConfig: LoggingConfig // the logging configuration for client requests
  val clusterServer: String // the URL of the target Kubernetes API server
  val namespaceName: String // the name of the configured namespace for this client
}
