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

import scala.language.implicitConversions
import scala.concurrent.Future


import skuber.json.ext.format._
import skuber.api.client._

package object ext {
  val extensionsAPIVersion = "extensions/v1beta1" 
  
  case class SubresourceReference(
      kind: String = "",
      name: String = "",
      apiVersion: String = extensionsAPIVersion,
      subresource: String = "")
      
  case class CPUTargetUtilization(targetPercentage: Int)

  trait IsExtensionsKind[T <: TypeMeta] { self: Kind[T] => 
    override def isExtensionsKind = true
  }
  
  // implicit kind values for the extensions group
  implicit val deploymentKind = new ObjKind[Deployment]("deployments","Deployment") with IsExtensionsKind[Deployment]
  implicit val hpasKind = new ObjKind[HorizontalPodAutoscaler]("horizontalpodautoscalers", "HorizontalPodAutoscaler") 
                                  with IsExtensionsKind[HorizontalPodAutoscaler]
  
  
  // Extensions Group API methods - for the moment this includes commands to get or change the scale on
  // a RC or Deployment Scale subresource, returning a Scale object with the updated spec and status.
  // (see [[http://kubernetes.io/v1.1/docs/design/horizontal-pod-autoscaler.html here]] for details about the
  // Scale subresource type). 
  // The standard K8SRequestContext RESTful API methods will work with Extensions API top-level 
  // resource types such as HorizontalPodAutoscaler and Deployment, so no additional methods are 
  // required here for creating, updating etc. these types.
  class ExtensionsGroupAPI(val context: K8SRequestContext)
  {
    implicit val executionContext = context.executionContext
    
    private[this] def getScale[O <: ObjectResource](objName: String)(implicit kind: ObjKind[O]) : Future[Scale] =
      executeScaleMethod(objName, "GET")(kind)
      
    private[this] def scale[O <: ObjectResource](objName: String, count: Int)(implicit kind: ObjKind[O]) : Future[Scale] = 
      executeScaleMethod(objName,
                         "PUT", 
                         Some(Scale(metadata=ObjectMeta(name=objName, namespace=context.namespaceName),
                                    spec=Scale.Spec(replicas=count))))(kind)
                                      
    private[this] def executeScaleMethod[O <: ObjectResource](
                            objName: String,
                            methodName: String,  
                            scale: Option[Scale] = None)(implicit kind: ObjKind[O]): Future[Scale] = {      
      
      val req=context.buildRequest(
                Some(objName + "/scale"), // sub resource name
                false,
                Some(true))(kind).
              withHeaders("Content-Type" -> "application/json").
              withMethod(methodName)
              
      val reqWithBody = scale map { s =>
        val body = scaleFormat.writes(s)
        context.logRequest(req, objName, Some(body))
        req.withBody(body) 
      } getOrElse {
        context.logRequest(req, objName)
        req
      }
     
      val updatedScaleFut = reqWithBody.execute()
      updatedScaleFut map toKubernetesResponse[Scale]
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
    def scale(de: Deployment, count: Int): Future[Scale] =
      scaleDeployment(de.name,  count)
      
    /*
     * Modify the specified replica count for a named replication controller, returning a Future with its
     * updated Scale subresource
     */
    def scaleReplicationController(name: String, count: Int): Future[Scale] =
      scale[ReplicationController](name, count)
      
    /*
     * Modify the specified replica count for a named Deployment, returning a Future with its
     * updated Scale subresource
     */     
    def scaleDeployment(name: String, count: Int): Future[Scale] =
      scale[Deployment](name, count)
      
    /*
     * Fetch the Scale subresource of a named Deployment
     * @returns a future containing the retrieved Scale subresource
     */
    def getDeploymentScale(objName: String) = getScale[Deployment](objName)
    
    /*
     * Fetch the Scale subresource of a named Replication Controller
     * @returns a future containing the retrieved Scale subresource
     */
    def getReplicationControllerScale(objName: String) = getScale[ReplicationController](objName)
  }
  
  // this implicit conversions makes the ExtensionsAPI methods available on a 
  // standard Skuber request context object
  implicit def k8sCtxToExtAPI(ctx: K8SRequestContext) = new ExtensionsGroupAPI(ctx)
}