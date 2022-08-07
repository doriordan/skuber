package skuber

/**
 * The skuber.ext package contains classes and methods for supporting the Kubernetes Extensions Group API.
 * This currently (Kubernetes V1.1) includes Scale, HorizontalPodAutoscaler, Deployment, Job, Ingress, DaemonSet
 * resource types.
 * Note that types in this group are not (yet) officially supported on Kubernetes, and so may be changed or removed in
 * future versions. Thus the 'skuber.ext' package might well be subject to more backwards-incompatible changes than
 * the main 'skuber' package, although both Skuber packages still have alpha release status.
 * Support for some of these types may not be enabled by default on the Kubernetes API server - see Kubernetes docs
 * for instructions on enabling them if necessary.
 * @author David O'Riordan
 */

import akka.http.scaladsl.marshalling.Marshal

import scala.language.implicitConversions
import scala.concurrent.Future
import skuber.json.ext.format._
import skuber.api.client._
import akka.http.scaladsl.model._
import skuber.json.PlayJsonSupportForAkkaHttp._
import skuber.networking.Ingress

package object ext {
  val extensionsAPIVersion = "extensions/v1beta1"

  @deprecated("Use type 'skuber.autoscaling.HorizontalPodAutoscaler' instead of 'skuber.ext.HorizontalPodAutoscaler'", "Skuber 1.7")
  type HorizontalPodAutoscaler = skuber.autoscaling.HorizontalPodAutoscaler
  @deprecated("Use type 'skuber.autoscaling.HorizontalPodAutoscalerList' instead of 'skuber.ext.HorizontalPodAutoscalerList'", "Skuber 1.7")
  type HorizontalPodAutoscalerList = skuber.autoscaling.HorizontalPodAutoscalerList

  type DaemonSetList = ListResource[DaemonSet]
  type ReplicaSetList = ListResource[ReplicaSet]
  type DeploymentList = ListResource[Deployment]

  // Extensions Group API methods - this just holds some deprecated methods for accessing scale subresources (update/get scale)
  // that are imported implicitly with this package import (i.e. `import skuber.ext._`). In the future this class will be removed,
  // so clients should use e.g. `k8s.scale[T](name,count)` instead where `T' is a supported resource type such as Deployment or StatefulSet.

  class ExtensionsGroupAPI(val context: K8SRequestContext)
  {
    /*
     * Modify the specified replica count for a replication controller, returning a Future with its
     * updated Scale subresource
     */
    @deprecated(message="Use method scale[ReplicationController](name,count) instead")
    def scale(rc: ReplicationController, count: Int): Future[Scale] =
      scaleReplicationController(rc.name, count)

    /*
     * Modify the specified replica count for a Deployment, returning a Future with its
     * updated Scale subresource
     */
    @deprecated(message="Use method scale[Deployment](name,count) instead")
    def scale(de: skuber.apps.Deployment, count: Int): Future[Scale] =
      scaleDeployment(de.name,  count)

    /*
     * Modify the specified replica count for a named replication controller, returning a Future with its
     * updated Scale subresource
     */
    @deprecated(message="Use method scale[ReplicationController](name,count) instead")
    def scaleReplicationController(name: String, count: Int)(implicit lc:LoggingContext=RequestLoggingContext()): Future[Scale] =
      context.scale[ReplicationController](name, count)

    /*
    * Modify the specified replica count for a named replica set, returning a Future with its
    * updated Scale subresource
    */
    @deprecated(message="Use method scale[ReplicaSet(name,count) instead")
    def scaleReplicaSet(name: String, count: Int)(implicit lc:LoggingContext=RequestLoggingContext()): Future[Scale] =
      context.scale[ReplicaSet](name, count)

    /*
     * Modify the specified replica count for a named Deployment, returning a Future with its
     * updated Scale subresource
     */
    @deprecated(message="Use method scale[Deployment](name,count) instead")
    def scaleDeployment(name: String, count: Int)(implicit lc:LoggingContext=RequestLoggingContext()): Future[Scale] =
      context.scale[Deployment](name, count)

    /*
     * Fetch the Scale subresource of a named Deployment
     * @returns a future containing the retrieved Scale subresource
     */
    @deprecated(message="Use method getScale[Deployment](name) instead")
    def getDeploymentScale(objName: String)(implicit lc:LoggingContext=RequestLoggingContext()): Future[Scale] = context.getScale[skuber.apps.Deployment](objName)

    /*
     * Fetch the Scale subresource of a named Replication Controller
     * @returns a future containing the retrieved Scale subresource
     */
    @deprecated(message="Use method getScale[ReplicationController](name) instead")
    def getReplicationControllerScale(objName: String)(implicit lc:LoggingContext=RequestLoggingContext()): Future[Scale] = context.getScale[ReplicationController](objName)

    /*
     * Fetch the Scale subresource of a named Replication Controller
     * @returns a future containing the retrieved Scale subresource
     */
    @deprecated(message="Use method getScale[ReplicaSet](name) instead")
    def getReplicaSetScale(objName: String)(implicit lc:LoggingContext=RequestLoggingContext()): Future[Scale] = context.getScale[ReplicaSet](objName)
  }

  // this implicit conversions makes the (deprecated) ExtensionsAPI methods available on a
  // standard Skuber request context object
  implicit def k8sCtxToExtAPI(ctx: K8SRequestContext): ExtensionsGroupAPI = new ExtensionsGroupAPI(ctx)
}
