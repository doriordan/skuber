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
   ) {
     override def toString : String = {
       var result = new StringBuilder("AuthInfo(")
       result ++= clientCertificate.map({
         case Left(certPath: String) => "clientCertificate=" + certPath + " "
         case Right(_: Array[Byte]) => "clientCertificate=<PEM masked> "
       }).getOrElse("")
       result ++= clientKey.map({
         case Left(certPath: String) => "clientKey=" + certPath + " "
         case Right(_: Array[Byte]) => "clientKey=<PEM masked> "
       }).getOrElse("")
       result ++= token.map("token="+_.replaceAll(".","*")+" ").getOrElse("")
       result ++= userName.map("userName="+_+" ").getOrElse("")
       result ++= password.map("password="+_.replaceAll(".","*")+" ").getOrElse("")
       result ++=")"
       result.toString()
     }
   }

   // for use with the Watch command
   case class WatchEvent[T <: ObjectResource](_type: EventType.Value, _object: T)
   object EventType extends Enumeration {
      type EventType = Value
      val ADDED,MODIFIED,DELETED,ERROR = Value
   }


   class RequestContext(createRequestFromUrl: String => WSRequest,
                        clusterServer: String,
                        requestAuth: HTTPRequestAuth.RequestAuth,
                        val namespaceName: String,
                        onClose: () => Unit)(implicit executorContext: ExecutionContext) {

     def executionContext = executorContext

     private[skuber] def buildRequest[T <: TypeMeta](
       rd: ResourceDefinition[_],
       nameComponent: Option[String],
       watch: Boolean = false,
       forExtensionsAPI: Option[Boolean] = None,
       namespace: String = namespaceName) : WSRequest =
     {
       val nsPathComponent = if (rd.spec.scope==ResourceSpecification.Scope.Namespaced) {
         Some("namespaces/" + namespaceName)
       } else {
         None
       }

       val watchPathComponent=if (watch) Some("watch") else None

       val k8sUrlOptionalParts = List(
         clusterServer,
         rd.spec.apiPathPrefix,
         rd.spec.group,
         rd.spec.version,
         watchPathComponent,
         nsPathComponent,
         rd.spec.names.plural,
         nameComponent)

       val k8sUrlParts = k8sUrlOptionalParts collect {
         case p: String if p != "" => p
         case Some(p: String) if p != "" => p
       }

       val k8sUrlStr = k8sUrlParts.mkString("/")

       val req = createRequestFromUrl(k8sUrlStr)
        HTTPRequestAuth.addAuth(req, requestAuth)
     }

     private[skuber] def logRequest(request: WSRequest, objName: String, json: Option[JsValue] = None, namespace: String = namespaceName) = {
       if (log.isInfoEnabled()) {
         val info = "method=" + request.method + ",url=" + request.url
         val debugInfo =
           if (log.isDebugEnabled())
             Some(",namespace=" + namespace + ",url=" + request.url +
                 request.queryString.headOption.map {
                   case (s, seq) => ",query='" + s + "=" + seq.headOption.getOrElse("") + "'"
                 }.getOrElse("")
                 + json.fold("")(js => ",body=" + js.toString()))
           else
             None
         log.info("[Skuber Request: " + info + debugInfo.getOrElse("") + "]")
       }
     }

     /**
       * Modify the specified K8S resource using a given HTTP method. The modified resource is returned.
       * The create, update and partiallyUpdate methods all call this, just passing different HTTP methods
       */
     private def modify[O <: ObjectResource](method: String)(obj: O)(implicit fmt: Format[O],rd: ResourceDefinition[O]): Future[O] = {
       val js = fmt.writes(obj)
       // if this is a POST we don't include the resource name in the URL
       val nameComponent=method match {
         case "POST" => None
         case _ => Some(obj.name)
       }
       val wsReq = buildRequest(rd, nameComponent).
                      withHeaders("Content-Type" -> "application/json").
                      withMethod(method).
                      withBody(js)
       logRequest(wsReq, obj.name, Some(js))

       val wsResponse = wsReq.execute()
       wsResponse map toKubernetesResponse[O]
     }

     def create[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O]) = modify("POST")(obj)
     def update[O <: ObjectResource](obj: O)(implicit fmt: Format[O],rd: ResourceDefinition[O]) = modify("PUT")(obj)
     def partiallyUpdate[O <: ObjectResource](obj: O)(implicit fmt: Format[O],rd: ResourceDefinition[O]) = modify("PATCH")(obj)

     def getNamespaceNames: Future[List[String]] = {
       list[NamespaceList].map {  namespaceList =>
         val namespaces  = namespaceList.items
         namespaces.map(_.name).toList
       }
     }

     /*
     * List by namespace returns a map of namespace (specified by name e.g. "default", "kube-sys") to the list of objects
     * of the specified kind in said namespace. All namespaces in the cluster are included in the map.
     * For example, it can be used to get a single list of all objects of the given kind across the whole cluster
     * e.g. val allPodsInCluster: Future[List[Pod]] = listByNamespace[Pod] map { _.values.flatMap(_.items) }
     * which supports the feature requested in issue #20
      */
     def listByNamespace[L <: ListResource[_]]()
        (implicit fmt: Format[L],rd: ResourceDefinition[L]): Future[Map[String,L]] =
     {
       listByNamespace[L](rd)
     }

     private def listByNamespace[L <: ListResource[_]](rd: ResourceDefinition[_])
         (implicit fmt: Format[L]): Future[Map[String,L]] =
     {
       val nsNamesFut: Future[List[String]] = getNamespaceNames
       val tuplesFut: Future[List[(String, L)]] = nsNamesFut flatMap { nsNames: List[String] =>
         Future.sequence(nsNames map { (nsName: String) =>
           listInNamespace[L](nsName, rd) map { l => (nsName,l) } } )
       }
       tuplesFut map {  _.toMap[String,L] }
     }

     /*
      * List all objects of given kind in the specified namespace on the cluster
      */
     def listInNamespace[L <: ListResource[_]](namespace: String)
      (implicit fmt: Format[L],rd: ResourceDefinition[L]) : Future[L] =
     {
       listInNamespace[L](namespace,rd)
     }

     private def listInNamespace[L <: ListResource[_]](namespace: String, rd: ResourceDefinition[_])
         (implicit fmt: Format[L]) : Future[L] =
     {
       val wsReq = buildRequest(rd, None, namespace = namespace)
       logRequest(wsReq, rd.spec.names.plural, None, namespace=namespace)
       val wsResponse = wsReq.get
       wsResponse map toKubernetesResponse[L]
     }

     /*
      * List in current namespace, selecting objects of given kind and with labels matching given selector
      */
     def list[L <: ListResource[_]]()(implicit fmt: Format[L],rd: ResourceDefinition[L]) : Future[L] = _list[L](rd, None)

     def list[L <: ListResource[_]](labelSelector: LabelSelector)(implicit fmt: Format[L],rd: ResourceDefinition[L]): Future[L] =
       _list[L](rd, Some(labelSelector))

     private def _list[L <: ListResource[_]](rd: ResourceDefinition[_], maybeLabelSelector: Option[LabelSelector])
         (implicit fmt: Format[L]) : Future[L] = {
       val wsReq = maybeLabelSelector.foldLeft( buildRequest(rd, None) ) {
         case (r,ls) => r.withQueryString(
           "labelSelector" -> ls.toString
         )
       }
       logRequest(wsReq, rd.spec.names.plural, None)
       val wsResponse = wsReq.get
       wsResponse map toKubernetesResponse[L]
     }

     def getOption[O <: ObjectResource](name: String)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[Option[O]] = {
       _get[O](name) map toKubernetesResponseOption[O] recover { case _ => None }
     }

     def get[O <: ObjectResource](name: String)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
       _get[O](name) map toKubernetesResponse[O]
     }

     def getInNamespace[O <: ObjectResource](name: String, namespace: String)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
       _get[O](name, namespace) map toKubernetesResponse[O]
     }

     private def _get[O <: ObjectResource](name: String, namespace: String = namespaceName)
         (implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[WSResponse] = {
       val wsReq = buildRequest(rd, Some(name),namespace=namespace)
       logRequest(wsReq, name, None,namespace=namespace)
       wsReq.get
     }

     def delete[O <: ObjectResource](name:String, gracePeriodSeconds: Int = 0)(implicit rd: ResourceDefinition[O]): Future[Unit] = {
       val options=DeleteOptions(gracePeriodSeconds=gracePeriodSeconds)
       val js = deleteOptionsWrite.writes(options)
       val wsReq = buildRequest(rd, Some(name)).
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

     def watch[O <: ObjectResource](obj: O)(implicit objfmt: Format[O],  rd: ResourceDefinition[O]) : Watch[WatchEvent[O]] = Watch.events(this, obj)
     def watch[O <: ObjectResource](name: String,
                                    sinceResourceVersion: Option[String] = None)
                                    (implicit objfmt: Format[O], rd: ResourceDefinition[O]) : Watch[WatchEvent[O]] =  Watch.events(this, name, sinceResourceVersion)

     // watch events on all objects of specified kind in current namespace
     def watchAll[O <: ObjectResource](sinceResourceVersion: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O])  =
             Watch.eventsOnKind[O](this,sinceResourceVersion)


     // get API versions supported by the cluster - against current v1.x versions of Kubernetes this returns just "v1"
     def getServerAPIVersions(): Future[List[String]] = {
       val url = clusterServer + "/api"
       val noAuthReq = createRequestFromUrl(url)
       val wsReq = HTTPRequestAuth.addAuth(noAuthReq, requestAuth)
       log.info("[Skuber Request: method=GET, resource=api/, description=APIVersions]")

       val wsResponse = wsReq.get
       wsResponse map toKubernetesResponse[APIVersions] map { _.versions }
     }

     def close = onClose()
   }

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

  private[skuber] def toKubernetesResponse[T](response: WSResponse)(implicit reader: Reads[T]) : T = {
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

  private[skuber] def toKubernetesResponseOption[T](response: WSResponse)(implicit reader: Reads[T]) : Option[T] = {
    checkResponseStatus(response)
    response.json.validate[T].asOpt
  }

  // check for non-OK status, throwing a K8SException if appropriate
  private def checkResponseStatus(response: WSResponse) : Unit ={
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

  private def buildBaseConfigForURL(url: Option[String]) = {
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

  def init(config: Configuration)(implicit executionContext : ExecutionContext): RequestContext = init(config.currentContext)

  def init(k8sContext: Context)(implicit executionContext : ExecutionContext): RequestContext = {
    val sslContext = TLS.establishSSLContext(k8sContext)
    val theRequestAuth = HTTPRequestAuth.establishRequestAuth(k8sContext)
    //      val wsConfig = new NingAsyncHttpClientConfigBuilder(WSClientConfig()).build
    //      val httpClient = new NingWSClient(wsConfig)
    val httpClientConfigBuilder = new AsyncHttpClientConfig.Builder
    sslContext foreach { ctx =>
      httpClientConfigBuilder.setSSLContext(ctx)
      // following is needed to prevent SSLv2Hello being used in SSL handshake - which Kubernetes doesn't like
      httpClientConfigBuilder.setEnabledProtocols(Array("TLSv1.2", "TLSv1"))
    }
    val httpClientConfig = httpClientConfigBuilder.build
    val theHttpClient = new NingWSClient(httpClientConfig)

    val theNamespaceName = k8sContext.namespace.name match {
      case "" => "default"
      case name => name
    }
    val requestMaker = (url:String) => theHttpClient.url(url)
    val close: () => Unit =  () => theHttpClient.close()
    new RequestContext(requestMaker, k8sContext.cluster.server, theRequestAuth,theNamespaceName, close)
  }


}
