package skuber.api

import java.net.URL

import com.ning.http.client.AsyncHttpClientConfig
import org.slf4j.LoggerFactory
import play.api.libs.json._
import play.api.libs.ws._
import play.api.libs.ws.ning._
import skuber._
import skuber.api.security.{HTTPRequestAuth, TLS}
import skuber.json.format._
import skuber.json.format.apiobj._

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author David O'Riordan
 */
package object client {

  val log = LoggerFactory.getLogger("skuber.api")

  // Certificates and keys can be specified in configuration either as paths to files or embedded PEM data
  type PathOrData = Either[String,Array[Byte]]

   // K8S client API classes
   val defaultApiServerURL = "http://localhost:8080"
   case class Cluster(
     apiVersion: String = "v1",
     server: String = defaultApiServerURL,
     insecureSkipTLSVerify: Boolean = false,
     certificateAuthority: Option[PathOrData] = None
   )

   case class Context(
     cluster: Cluster = Cluster(),
     authInfo: AuthInfo = AuthInfo(),
     namespace: Namespace = Namespace.default
   )

   case class AuthInfo(
     clientCertificate: Option[PathOrData] = None,
     clientKey: Option[PathOrData] = None,
     token: Option[String] = None,
     userName: Option[String] = None,
     password: Option[String] = None
   )

   // for use with the Watch command
   case class WatchEvent[T <: ObjectResource](_type: EventType.Value, _object: T)
   object EventType extends Enumeration {
      type EventType = Value
      val ADDED,MODIFIED,DELETED,ERROR = Value
   }


   class RequestContext(k8sContext: Context)(implicit executorContext: ExecutionContext) {
      val sslContext = TLS.establishSSLContext(k8sContext)
      val requestAuth = HTTPRequestAuth.establishRequestAuth(k8sContext)
//      val wsConfig = new NingAsyncHttpClientConfigBuilder(WSClientConfig()).build
//      val httpClient = new NingWSClient(wsConfig)
     val httpClientConfigBuilder = new AsyncHttpClientConfig.Builder
     sslContext foreach { ctx =>
        httpClientConfigBuilder.setSSLContext(ctx)
        // following is needed to prevent SSLv2Hello being used in SSL handshake - which Kubernetes doesn't like
        httpClientConfigBuilder.setEnabledProtocols(Array("TLSv1.2","TLSv1"))
     }
     val httpClientConfig = httpClientConfigBuilder.build
     val httpClient = new NingWSClient(httpClientConfig)

     val namespaceName = k8sContext.namespace.name match {
        case "" => "default"
        case name => name
     }

     val nsPathComponent =  Some("namespaces/" + namespaceName)

     def executionContext = executorContext

     def buildRequest[T <: TypeMeta](
       nameComponent: Option[String],
       watch: Boolean = false,
       forExtensionsAPI: Option[Boolean] = None
       )(implicit kind: Kind[T]) : WSRequest =
     {
       val kindComponent = kind.urlPathComponent
       val apiVersion = kind.apiVersion
       val usesExtensionsAPI = forExtensionsAPI.getOrElse(kind.isExtensionsKind)
       val usesBatchAPI = forExtensionsAPI.getOrElse(kind.isBatchKind)
       val apiPrefix = if (usesExtensionsAPI || usesBatchAPI) "apis" else "api"

         // helper to compose a full URL from a sequence of path components
       def mkUrlString(pathComponents: Option[String]*) : String = {
         pathComponents.foldLeft("")((acc,next) => next match {
           case None => acc
           case Some(pathComponent) => {
             acc match {
               case "" => pathComponent
               case _ => acc + "/" + pathComponent
             }
           }
         })
       }

       val watchPathComponent=if (watch) Some("watch") else None

       val k8sUrlStr = mkUrlString(
         Some(k8sContext.cluster.server),
         Some(apiPrefix),
         Some(apiVersion),
         watchPathComponent,
         if (kind.isNamespaced) nsPathComponent else None,
         Some(kindComponent),
         nameComponent)

       val req = httpClient.url(k8sUrlStr)
        HTTPRequestAuth.addAuth(req, requestAuth)
      }

      def logRequest(request: WSRequest, objName: String, json: Option[JsValue] = None) : Unit =
        if (log.isInfoEnabled())
        {
           val info = "method=" + request.method + ",url=" + request.url
           val debugInfo =
             if (log.isDebugEnabled())
                             Some(",namespace=" + namespaceName + ",url=" + request.url +
                                       request.queryString.headOption.map {
                                         case (s, seq) => ",query='" + s + "=" + seq.headOption.getOrElse("") + "'"
                                       }.getOrElse("")
                                    + json.fold("")(js => ",body=" + js.toString()))
             else
               None
           log.info("[Skuber Request: " + info + debugInfo.getOrElse("") + "]")
        }

      /**
       * Modify the specified K8S resource using a given HTTP method. The modified resource is returned.
       * The create, update and partiallyUpdate methods all call this, just passing different HTTP methods
       */
      def modify[O <: ObjectResource](method: String)(obj: O)(implicit fmt: Format[O], kind: Kind[O]): Future[O] = {
        val js = fmt.writes(obj)
        // if this is a POST we don't include the resource name in the URL
        val nameComponent=method match {
          case "POST" => None
          case _ => Some(obj.name)
        }
        val wsReq = buildRequest(nameComponent)(kind).
                      withHeaders("Content-Type" -> "application/json").
                      withMethod(method).
                      withBody(js)
        logRequest(wsReq, obj.name, Some(js))

        val wsResponse = wsReq.execute()
        wsResponse map toKubernetesResponse[O]
      }

      def create[O <: ObjectResource](obj: O)(implicit fmt: Format[O], kind: ObjKind[O]) = modify("POST")(obj)
      def update[O <: ObjectResource](obj: O)(implicit fmt: Format[O],  kind: ObjKind[O]) = modify("PUT")(obj)
      def partiallyUpdate[O <: ObjectResource](obj: O)(implicit fmt: Format[O],  kind: ObjKind[O]) = modify("PATCH")(obj)


     def list[L <: KList[_]]()(implicit fmt: Format[L], kind: ListKind[L]) : Future[L] =
     {
       val wsReq = buildRequest(None)(kind)
       logRequest(wsReq, kind.urlPathComponent, None)
       val wsResponse = wsReq.get
       wsResponse map toKubernetesResponse[L]
     }

     def getOption[O <: ObjectResource](name: String)(implicit fmt: Format[O], kind: ObjKind[O]): Future[Option[O]] = {
       _get[O](name) map toKubernetesResponseOption[O] recover { case _ => None }
     }

     def get[O <: ObjectResource](name: String)(implicit fmt: Format[O], kind: ObjKind[O]): Future[O] = {
       _get[O](name) map toKubernetesResponse[O]
     }

     private def _get[O <: ObjectResource](name: String)(implicit fmt: Format[O], kind: ObjKind[O]): Future[WSResponse] = {
       val wsReq = buildRequest(Some(name))(kind)
       logRequest(wsReq, name, None)
       wsReq.get
     }

     def delete[O <: ObjectResource](name:String, gracePeriodSeconds: Int = 0)(implicit kind: ObjKind[O]): Future[Unit] = {
       val options=DeleteOptions(gracePeriodSeconds=gracePeriodSeconds)
       val js = deleteOptionsWrite.writes(options)
       val wsReq = buildRequest(Some(name))(kind).
                      withHeaders("Content-Type" -> "application/json").
                      withBody(js).withMethod("DELETE")
       logRequest(wsReq, name, None)
       val wsResponse = wsReq.delete
       wsResponse map checkResponseStatus
     }


     // The Watch methods place a Watch on the specified resource on the Kubernetes cluster.
     // The methods return Play Framework enumerators that will reactively emit a stream of updated
     // values of the watched resources.

     import play.api.libs.iteratee.Enumerator

     def watch[O <: ObjectResource](obj: O)(implicit objfmt: Format[O],  kind: ObjKind[O]) : Watch[WatchEvent[O]] = Watch.events(this, obj)       
     def watch[O <: ObjectResource](name: String,
                                    sinceResourceVersion: Option[String] = None)
                                    (implicit objfmt: Format[O], kind: ObjKind[O]) : Watch[WatchEvent[O]] =  Watch.events(this, name, sinceResourceVersion)

     // watch events on all objects of specified kind in current namespace
     def watchAll[O <: ObjectResource](sinceResourceVersion: Option[String] = None)(implicit fmt: Format[O], kind: ObjKind[O])  =
             Watch.eventsOnKind[O](this,sinceResourceVersion)


     // get API versions supported by the cluster - against current v1.x versions of Kubernetes this returns just "v1"
     def getServerAPIVersions(): Future[List[String]] = {
       val url = k8sContext.cluster.server + "/api"
       val noAuthReq = httpClient.url(url)
       val wsReq = HTTPRequestAuth.addAuth(noAuthReq, requestAuth)
       log.info("[Skuber Request: method=GET, resource=api/, description=APIVersions]")

       val wsResponse = wsReq.get
       wsResponse map toKubernetesResponse[APIVersions] map { _.versions }
     }

     def close = {
        httpClient.close
     }
   }

   // basic resource kinds supported by the K8S API server
   abstract class Kind[T <: TypeMeta](implicit fmt: Format[T]) {
     def urlPathComponent: String
     def isExtensionsKind: Boolean = false
     def isBatchKind: Boolean = false
     def isNamespaced: Boolean = true
     def apiVersion: String =
       if (isExtensionsKind)
         skuber.ext.extensionsAPIVersion
       else if (isBatchKind)
         skuber.batch.batchAPIVersion
       else
         "v1"
   }

   case class ObjKind[O <: ObjectResource](
       val urlPathComponent: String,
       kind: String)(implicit fmt: Format[O])
       extends Kind[O]

   implicit val podKind = ObjKind[Pod]("pods", "Pod")
   implicit val nodeKind = new ObjKind[Node]("nodes", "Node") { override def isNamespaced = false }
   implicit val serviceKind = ObjKind[Service]("services", "Service")
   implicit val replCtrllrKind = ObjKind[ReplicationController]("replicationcontrollers", "ReplicationController")
   implicit val endpointsKind = ObjKind[Endpoints]("endpoints", "Endpoints")
   implicit val namespaceKind = new ObjKind[Namespace]("namespaces", "Namespace") { override def isNamespaced = false }
   implicit val persistentVolumeKind = ObjKind[PersistentVolume]("persistentvolumes", "PersistentVolume")
   implicit val persistentVolumeClaimsKind = ObjKind[PersistentVolumeClaim]("persistentvolumeclaims", "PersistentVolumeClaim")
   implicit val serviceAccountKind = ObjKind[ServiceAccount]("serviceaccounts","ServiceAccount")
   implicit val secretKind = ObjKind[Secret]("secrets","Secret")
   implicit val podTemplateKind = ObjKind[Pod.Template]("podtemplates", "PodTemplate")
   implicit val limitRangeKind = ObjKind[LimitRange]("limitranges","LimitRange")
   implicit val resourceQuotaKind = ObjKind[Resource.Quota]("resourcequotas", "ResourceQuota")
   implicit val configMapKind = ObjKind[ConfigMap]("configmaps", "ConfigMap")

   case class ListKind[L <: TypeMeta](val urlPathComponent: String, val apiPrefix: String = "api")(implicit fmt: Format[L])
     extends Kind[L]
   implicit val podListKind = ListKind[PodList]("pods")
   implicit val nodeListKind = new ListKind[NodeList]("nodes") { override def isNamespaced = false }
   implicit val serviceListKind = ListKind[ServiceList]("services")
   implicit val endpointListKind = ListKind[EndpointList]("endpoints")
   implicit val eventListKind = ListKind[EventList]("events")
   implicit val replCtrlListKind = ListKind[ReplicationControllerList]("replicationcontrollers")
   implicit val namespaceListKind = new ListKind[NamespaceList]("namespaces") { override def isNamespaced = false }
   implicit val persistentVolumeListKind = ListKind[PersistentVolumeList]("persistentvolumes")
   implicit val persistentVolumeClaimListKind = ListKind[PersistentVolumeClaimList]("persistentvolumeclaims")
   implicit val serviceAccountListKind = ListKind[ServiceAccountList]("serviceaccounts")
   implicit val limitRangeListKind = ListKind[LimitRangeList]("limitranges")
   implicit val resourceQuotaListKind = ListKind[ResourceQuotaList]("resourcequotas")
   implicit val secretListKind = ListKind[SecretList]("secrets")

  // Status will usually be returned by Kubernetes when an error occurs with a request
  case class Status(
    apiVersion: String = "v1",
    kind: String = "Status",
    metadata: ListMeta = ListMeta(),
    status: Option[String] =None,
    message: Option[String]=None,
    reason: Option[String]=None,
    details: Option[Any] = None,
    code: Option[Int] = None  // HTTP status code
  )

  class K8SException(val status: Status) extends RuntimeException (status.toString) // we throw this when we receive a non-OK response

  def toKubernetesResponse[T](response: WSResponse)(implicit reader: Reads[T]) : T = {
    checkResponseStatus(response)
    val result = response.json.validate[T]
    result recover { case errors =>
        val status =
          Status(message = Some(errors.toString),
                 reason = Some("validation error mapping response body to expected Scala class"),
                 details = Some(response.body),
                 code = Some(response.status))
        throw new K8SException(status)
    }
    if (log.isInfoEnabled)
      log.info("[Skuber Response: successfully parsed " + result.get)
    result.get
  }

  def toKubernetesResponseOption[T](response: WSResponse)(implicit reader: Reads[T]) : Option[T] = {
    checkResponseStatus(response)
    response.json.validate[T].asOpt
  }

  // check for non-OK status, throwing a K8SException if appropriate
  def checkResponseStatus(response: WSResponse) : Unit ={
    response.status match {
      case code if code < 300 => // ok
      case code => {
        // a non-success or unexpected status returned - we should normally have a Status in the response body
        val status=response.json.validate[Status]
        status match {
          case JsSuccess(status, path) =>
            if (log.isWarnEnabled)
              log.warn("[Skuber Response: non-ok status returned = " + status)
              throw new K8SException(status)
          case JsError(e) => // unexpected response, so generate a Status
            val status=Status(message=Some("Unexpected response body for non-OK status "),
                              reason=Some(response.statusText),
                              details=Some(response.body),
                              code=Some(response.status))
            if (log.isErrorEnabled)
              log.error("[Skuber Response: status code = " + code + ", error parsing body = " + e)
            throw new K8SException(status)
        }
      }
    }
  }

  // Delete options are passed with a Delete request
  case class DeleteOptions(
    apiVersion: String = "v1",
    kind: String = "DeleteOptions",
    gracePeriodSeconds: Int = 0)

  def buildBaseConfigForURL(url: Option[String]) = {
    val cluster = Cluster(server=url.getOrElse(defaultApiServerURL))
    val context = Context(cluster=cluster)
    Configuration().useContext(context)
  }

  def init(implicit executionContext : ExecutionContext): RequestContext = {
    // Initialising without explicit Configuration.
    // The K8S Configuration applied will be determined by the the environment variable 'SKUBERCONFIG'.
    // If SKUBERCONFIG value matches:
    // - Empty / Not Set:  Configure to connect via default localhost URL
    // -
    // - "file" : Configure from kubeconfig file at default location (~/.kube/config)
    // - "file://<path>: Configure from the kubeconfig file at the specified location
    // Further the base server URL if kubectl proxy is determined by SKUBERPROXY - or just
    // 'http://localhost:8080' if not set.
    // Note that the configurations that use the kubectl proxy assumes default namespace context, and delegates auth etc.
    // to the proxy.
    // If configured to use  a kubeconfig file note that any certificate/key data in the file will be ignored - any
    // required key/cert data for TLS connections must be installed in the applicable Java keystore/truststore.
    //
    val skuberConfigEnv = sys.env.get("SKUBER_CONFIG")
    val skuberUrlEnv = sys.env.get("SKUBER_URL")
    val config : Configuration = skuberConfigEnv match {
      case Some(conf) if conf == "file" =>  Configuration.parseKubeconfigFile().get
      case Some(fileUrl) => {
        val path = java.nio.file.Paths.get(new URL(fileUrl).toURI)
        Configuration.parseKubeconfigFile(path).get
      }
      case None => buildBaseConfigForURL(skuberUrlEnv)
    }
    init(config)
  }

  def init(config: Configuration)(implicit executionContext : ExecutionContext) = new RequestContext(config.currentContext)
}
