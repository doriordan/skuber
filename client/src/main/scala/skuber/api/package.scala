package skuber.api

import play.api.libs.ws._
import play.api.libs.ws.ning._

import java.net.URL

import scala.concurrent.{Future,ExecutionContext}
import skuber.model.coretypes._
import skuber.model._

// import all the implicit formatters for the skuber model
import skuber.json.format._
import play.api.libs.json.{Format, JsResult, JsSuccess, JsError}
import play.api.http.Writeable._


/**
 * @author David O'Riordan
 */
package object client {

   // K8S client API classes
   
   case class K8SCluster(
     apiVersion: String = "v1",  
     server: String = "http://localhost:8001",
     insecureSkipTLSVerify: Boolean = false
   )
   
   case class K8SContext(
     cluster: K8SCluster = K8SCluster(), 
     authInfo: K8SAuthInfo = K8SAuthInfo(),
     namespace: Namespace = Namespace.default 
   )
     
   case class K8SAuthInfo(
     token: Option[String] = None,
     userName: Option[String] = None,
     password: Option[String] = None
   ) 
   
   class K8SRequestContext(implicit val k8sContext: K8SContext, val executorContext: ExecutionContext) { 
      val sslCtxt = Auth.establishSSLContext(k8sContext)
      val auth = Auth.establishClientAuth(k8sContext)
      val wsConfig = new NingAsyncHttpClientConfigBuilder(WSClientConfig()).build
      val ningClient = new NingWSClient(wsConfig)
   
      val nsPathComponent = k8sContext.namespace match {
            case Namespace.default => None // default namespace - just omit from URL
            case ns => Some("namespaces/" + ns.name)
      }
      
      private def buildRequest(kindComponent: Option[String],
                               nameComponent: Option[String])
       : WSRequest = 
      {  
        // helper to compose a full URL from a sequence of path components
        def mkUrlString(pathComponents: Option[String]*) : String = {
          pathComponents.foldLeft("")((acc,next) => next match {
            case None => acc
            case Some(pathComponent) => acc + "/" + pathComponent
          })
        }
        
        val k8sUrlStr = mkUrlString(
          Some(k8sContext.cluster.server), 
          Some("api"), 
          Some("v1"), 
          nsPathComponent, 
          kindComponent,
          nameComponent)
                             
        val url = ningClient.url(k8sUrlStr)
        Auth.addAuth(url, auth)
      }
      
      def create[T <: TypeMeta](obj: T)(implicit fmt: Format[T], kind: Kind[T]) : Future[Status] = 
      {
        val js = fmt.writes(obj)
        val wsReq = buildRequest(Some(kind.urlPathComponent), None).
                      withHeaders("contentType" -> "application/json")
        val wsResponse = wsReq.post(js)
        wsResponse map { res => Status(res.status) }
     }
   
     def get[L <: KList[_]](implicit fmt: Format[L], kind: ListKind[L]) : Future[L] = 
     {
       val wsReq = buildRequest(Some(kind.urlPathComponent),None)
       val wsResponse = wsReq.get
       wsResponse map {
         response => 
           val result = fmt.reads(response.json)
           result match {
             case JsSuccess(klist, _) => klist
             case err@JsError(_) => throw new Exception("Error parsing Kubernetes object: " + err.toString)
           }
       }
     }
     def get[O <: ObjectResource](name: String)(implicit fmt: Format[O], kind: ObjKind[O]): Future[Result[O]] = ???
     
   }
   
   // basic resource kinds supported by the K8S API server
   abstract class Kind[T <: TypeMeta](implicit fmt: Format[T]) { def urlPathComponent: String }
   
   case class ObjKind[O <: ObjectResource](val urlPathComponent: String)(implicit fmt: Format[O]) 
       extends Kind[O]
     
   implicit val podKind = ObjKind[Pod]("pods")
   implicit val nodeKind = ObjKind[Node]("nodes")
   implicit val serviceKind = ObjKind[Service]("services")
   implicit val replCtrllrKind = ObjKind[ReplicationController]("replicationcontrollers")
   implicit val endpointsKind = ObjKind[Endpoints]("endpoints")

   case class ListKind[L <: KList[_]](val urlPathComponent: String)(implicit fmt: Format[L]) 
     extends Kind[L]
   implicit val podListKind = ListKind[PodList]("pods")
   implicit val nodeListKind = ListKind[NodeList]("nodes")
   implicit val serviceListKind = ListKind[ServiceList]("services")
   implicit val replCtrlListKind = ListKind[ReplicationControllerList]("replicationcontrollers")
   implicit val eventListKind = ListKind[EventList]("events")
   
   case class Status(httpStatusCode: Int)
   class Result[T](response: WSResponse) {
     
   }
}