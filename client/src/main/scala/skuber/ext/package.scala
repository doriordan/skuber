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

package object ext {
  val extensionsAPIVersion = "extensions/v1beta1"

  @deprecated("Use type 'skuber.autoscaling.HorizontalPodAutoscaler' instead of 'skuber.ext.HorizontalPodAutoscaler'", "Skuber 1.7")
  type HorizontalPodAutoscaler = skuber.autoscaling.HorizontalPodAutoscaler
  @deprecated("Use type 'skuber.autoscaling.HorizontalPodAutoscalerList' instead of 'skuber.ext.HorizontalPodAutoscalerList'", "Skuber 1.7")
  type HorizontalPodAutoscalerList = skuber.autoscaling.HorizontalPodAutoscalerList

  type DaemonSetList = ListResource[DaemonSet]
  type ReplicaSetList = ListResource[ReplicaSet]
  type IngressList = ListResource[Ingress]
  type DeploymentList = ListResource[Deployment]

  // Extensions Group API methods - for the moment this includes commands to get or change the scale on
  // a RC or Deployment Scale subresource, returning a Scale object with the updated spec and status.
  // (see [[http://kubernetes.io/v1.1/docs/design/horizontal-pod-autoscaler.html here]] for details about the
  // Scale subresource type). 
  // The standard K8SRequestContext RESTful API methods will work with Extensions API top-level 
  // resource types such as HorizontalPodAutoscaler and Deployment, so no additional methods are 
  // required here for creating, updating etc. these types.
  //
  // NOTE: in a future release these scale methods are likely to be moved or modified

  class ExtensionsGroupAPI(val context: K8SRequestContext)
  {
    private[this] def getScale[O <: ObjectResource](objName: String)(
      implicit rd: ResourceDefinition[O], lc: LoggingContext) : Future[Scale] =
    {
      val req = context.buildRequest(HttpMethods.GET, rd, Some(objName+ "/scale"))
      context.logRequest(req)
      context.makeRequestReturningObjectResource[Scale](req)
    }

    private[this] def scale[O <: ObjectResource](
      apiVersion: String,
      objName: String,
      count: Int)(implicit rd: ResourceDefinition[O], lc:LoggingContext): Future[Scale] =
    {
      val scale = Scale(
        apiVersion = apiVersion,
        metadata = ObjectMeta(name = objName, namespace = context.namespaceName),
        spec = Scale.Spec(replicas = count)
      )
      implicit val dispatcher=context.actorSystem.dispatcher
      val marshal = Marshal(scale)
      for {
        requestEntity <- marshal.to[RequestEntity]
        httpRequest = context
              .buildRequest(HttpMethods.PUT, rd, Some(s"${objName}/scale"))
              .withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
        _ = context.logRequest(httpRequest)
        scaledResource <- context.makeRequestReturningObjectResource[Scale](httpRequest)
      } yield scaledResource
    }

    /*
     * Modify the specified replica count for a replication controller, returning a Future with its
     * updated Scale subresource
     */
    def scale(rc: ReplicationController, count: Int): Future[Scale] =
      scaleReplicationController(rc.name, count)
     
    /*
     * Modify the specified replica count for a Deployment, returning a Future with its
     * updated Scale subresource
     */  
    def scale(de: skuber.apps.Deployment, count: Int): Future[Scale] =
      scaleDeployment(de.name,  count)
      
    /*
     * Modify the specified replica count for a named replication controller, returning a Future with its
     * updated Scale subresource
     */
    def scaleReplicationController(name: String, count: Int)(implicit lc:LoggingContext=RequestLoggingContext()): Future[Scale] =
      scale[ReplicationController]("autoscaling/v1", name, count)

    /*
    * Modify the specified replica count for a named replica set, returning a Future with its
    * updated Scale subresource
    */
    def scaleReplicaSet(name: String, count: Int)(implicit lc:LoggingContext=RequestLoggingContext()): Future[Scale] =
      scale[ReplicaSet]("extensions/v1beta1", name, count)

    /*
     * Modify the specified replica count for a named Deployment, returning a Future with its
     * updated Scale subresource
     */     
    def scaleDeployment(name: String, count: Int)(implicit lc:LoggingContext=RequestLoggingContext()): Future[Scale] =
      scale[skuber.apps.Deployment]("apps/v1beta1", name, count)
      
    /*
     * Fetch the Scale subresource of a named Deployment
     * @returns a future containing the retrieved Scale subresource
     */
    def getDeploymentScale(objName: String)(implicit lc:LoggingContext=RequestLoggingContext()) = getScale[skuber.apps.Deployment](objName)
    
    /*
     * Fetch the Scale subresource of a named Replication Controller
     * @returns a future containing the retrieved Scale subresource
     */
    def getReplicationControllerScale(objName: String)(implicit lc:LoggingContext=RequestLoggingContext()) = getScale[ReplicationController](objName)

    /*
     * Fetch the Scale subresource of a named Replication Controller
     * @returns a future containing the retrieved Scale subresource
     */
    def getReplicaSetScale(objName: String)(implicit lc:LoggingContext=RequestLoggingContext()) = getScale[ReplicaSet](objName)

  }
  
  // this implicit conversions makes the ExtensionsAPI methods available on a 
  // standard Skuber request context object
  implicit def k8sCtxToExtAPI(ctx: K8SRequestContext) = new ExtensionsGroupAPI(ctx)
}
