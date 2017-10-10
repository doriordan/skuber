package skuber.api

import java.net.URL

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.{Format, Reads}
import skuber._
import skuber.api.security.{HTTPRequestAuth, TLS}
import skuber.json.format._
import skuber.json.format.apiobj._
import skuber.json.PlayJsonSupportForAkkaHttp._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import skuber.api.client.Status
import skuber.json

/**
 * @author David O'Riordan
 */
package object client {

  val log: Logger = LoggerFactory.getLogger("skuber.api")

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

   class RequestContext(val requestMaker: (Uri, HttpMethod)  => HttpRequest,
                        val requestInvoker: HttpRequest => Future[HttpResponse],
                        val clusterServer: String,
                        requestAuth: HTTPRequestAuth.RequestAuth,
                        val namespaceName: String,
                        val closeHook: Option[() => Unit] = None)
                        (implicit val actorSystem: ActorSystem, val actorMaterializer: ActorMaterializer) {

     implicit val dispatcher = actorSystem.dispatcher

     var closed = false

     private[skuber] def invoke(request: HttpRequest): Future[HttpResponse] = {
       if (closed)
         throw new IllegalStateException("Request context has been closed")
       requestInvoker(request)
     }

     private[skuber] def buildRequest[T <: TypeMeta](
       method: HttpMethod,
       rd: ResourceDefinition[_],
       nameComponent: Option[String],
       query: Option[Uri.Query] = None,
       watch: Boolean = false,
       namespace: String = namespaceName): HttpRequest =
     {
       val nsPathComponent = if (rd.spec.scope == ResourceSpecification.Scope.Namespaced) {
         Some("namespaces/" + namespace)
       } else {
         None
       }

       val watchPathComponent = if (watch) Some("watch") else None

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

       val uri = query.map { q =>
         Uri(k8sUrlStr).withQuery(q)
       }.getOrElse {
         Uri(k8sUrlStr)
       }

       val req = requestMaker(uri, method)
       HTTPRequestAuth.addAuth(req, requestAuth)
     }

     private[skuber] def logRequest(request: HttpRequest) = {
       if (log.isInfoEnabled()) {
         val info = s"${request.method.value} ${request.uri.toString}"
         log.info(s"[Skuber making request: $info]")
       }
     }

     private[skuber] def sendRequestAndUnmarshalResponse[O](httpRequest: HttpRequest)(implicit fmt: Format[O]): Future[O] = {
       logRequest(httpRequest)
       for {
         httpResponse <- invoke(httpRequest)
         result <- toKubernetesResponse[O](httpResponse)
       } yield result
     }

     /**
       * Modify the specified K8S resource using a given HTTP method. The modified resource is returned.
       * The create, update and partiallyUpdate methods all call this, just passing different HTTP methods
       */
     private[skuber] def modify[O <: ObjectResource](method: HttpMethod)(obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
       // if this is a POST we don't include the resource name in the URL
       val nameComponent: Option[String] = method match {
         case HttpMethods.POST => None
         case _ => Some(obj.name)
       }
       modify(method, obj, nameComponent)
     }

     private[skuber] def  modify[O <: ObjectResource](method: HttpMethod, obj: O, nameComponent: Option[String])(
       implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] =
     {
       val marshal = Marshal(obj)
       for {
         requestEntity <- marshal.to[RequestEntity]
         httpRequest = buildRequest(method, rd, nameComponent).withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
         newOrUpdatedResource <- sendRequestAndUnmarshalResponse[O](httpRequest)
       } yield newOrUpdatedResource
     }

     def create[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
       modify(HttpMethods.POST)(obj)
     }

     def update[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
       modify(HttpMethods.PUT)(obj)
     }

     def partiallyUpdate[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
       modify(HttpMethods.PATCH)(obj)
     }

     def getNamespaceNames: Future[List[String]] = {
       list[NamespaceList].map { namespaceList =>
         val namespaces = namespaceList.items
         namespaces.map(_.name)
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
         (implicit fmt: Format[L], rd: ResourceDefinition[L]): Future[Map[String, L]] = {
       listByNamespace[L](rd)
     }

     private def listByNamespace[L <: ListResource[_]](rd: ResourceDefinition[_])
         (implicit fmt: Format[L]): Future[Map[String, L]] = {
       val nsNamesFut: Future[List[String]] = getNamespaceNames
       val tuplesFut: Future[List[(String, L)]] = nsNamesFut flatMap { nsNames: List[String] =>
         Future.sequence(nsNames map { (nsName: String) =>
           listInNamespace[L](nsName, rd) map { l => (nsName, l) }
         })
       }
       tuplesFut map {
         _.toMap[String, L]
       }
     }

     /*
      * List all objects of given kind in the specified namespace on the cluster
      */
     def listInNamespace[L <: ListResource[_]](theNamespace: String)
         (implicit fmt: Format[L], rd: ResourceDefinition[L]): Future[L] = {
       listInNamespace[L](theNamespace, rd)
     }

     private def listInNamespace[L <: ListResource[_]](theNamespace: String, rd: ResourceDefinition[_])
         (implicit fmt: Format[L]): Future[L] = {
       val req = buildRequest(HttpMethods.GET, rd, None, namespace = theNamespace)
       sendRequestAndUnmarshalResponse[L](req)
     }

     /*
      * List in current namespace, selecting objects of given kind and with labels matching given selector
      */
     def list[L <: ListResource[_]]()(implicit fmt: Format[L], rd: ResourceDefinition[L]): Future[L] = _list[L](rd, None)

     def list[L <: ListResource[_]](labelSelector: LabelSelector)(implicit fmt: Format[L], rd: ResourceDefinition[L]): Future[L] =
       _list[L](rd, Some(labelSelector))

     private def _list[L <: ListResource[_]](rd: ResourceDefinition[_], maybeLabelSelector: Option[LabelSelector])
         (implicit fmt: Format[L]): Future[L] = {
       val queryOpt = maybeLabelSelector map { ls =>
         Uri.Query("labelSelector" -> ls.toString)
       }
       val req = buildRequest(HttpMethods.GET, rd, None, query = queryOpt)
       sendRequestAndUnmarshalResponse[L](req)
     }

     def getOption[O <: ObjectResource](name: String)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[Option[O]] = {
       _get[O](name) map { result =>
         Some(result)
       } recover {
         case ex: K8SException if ex.status.code.contains(StatusCodes.NotFound.intValue) => None
       }
     }

     def get[O <: ObjectResource](name: String)(implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
       _get[O](name)
     }

     def getInNamespace[O <: ObjectResource](name: String, namespace: String)(
       implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
       _get[O](name, namespace)
     }

     private[api] def _get[O <: ObjectResource](name: String, namespace: String = namespaceName)(
       implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[O] = {
       val req = buildRequest(HttpMethods.GET, rd, Some(name), namespace = namespace)
       sendRequestAndUnmarshalResponse[O](req)
     }

     def delete[O <: ObjectResource](name: String, gracePeriodSeconds: Int = 0)(
       implicit rd: ResourceDefinition[O]): Future[Unit] = {
       val options = DeleteOptions(gracePeriodSeconds = gracePeriodSeconds)
       import skuber.json.format.apiobj.deleteOptionsWrite
       val marshalledOptions = Marshal(options)
       for {
         requestEntity <- marshalledOptions.to[RequestEntity]
         request = buildRequest(HttpMethods.DELETE, rd, Some(name)).withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
         _ = logRequest(request)
         response <- invoke(request)
         _ <- checkResponseStatus(response)
       } yield ()
     }

     def watch[O <: ObjectResource](obj: O)(
       implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[Source[WatchEvent[O], _]] =
     {
       watch(obj.name)
     }

     // The Watch methods place a Watch on the specified resource on the Kubernetes cluster.
     // The methods return Play Framework enumerators that will reactively emit a stream of updated
     // values of the watched resources.
     def watch[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
       implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[Source[WatchEvent[O], _]] =
     {
       Watch.events(this, name, sinceResourceVersion, bufSize)
     }

     // watch events on all objects of specified kind in current namespace
     def watchAll[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
       implicit fmt: Format[O], rd: ResourceDefinition[O]): Future[Source[WatchEvent[O], _]] =
     {
       Watch.eventsOnKind[O](this, sinceResourceVersion, bufSize)
     }

     // get API versions supported by the cluster
     def getServerAPIVersions: Future[List[String]] = {
       val url = clusterServer + "/api"
       val noAuthReq = requestMaker(Uri(url), HttpMethods.GET)
       val request = HTTPRequestAuth.addAuth(noAuthReq, requestAuth)
       log.info("[Skuber Request: method=GET, resource=api/, description=APIVersions]")
       for {
         response <- invoke(request)
         apiVersionResource <- toKubernetesResponse[APIVersions](response)
       } yield (apiVersionResource.versions)
     }

     def close: Unit = {
       closed = true
       closeHook map {
         _ ()
       } // invoke the specified close hook if specified
     }

     private[skuber] def toKubernetesResponse[T](response: HttpResponse)(implicit reader: Reads[T]): Future[T] = {
       val statusOptFut = checkResponseStatus(response)
       statusOptFut flatMap { statusOpt =>
         statusOpt match {
           case Some(status) => throw new K8SException(status)
           case None => Unmarshal(response).to[T]
         }
       }
     }

     // check for non-OK status, returning (in a Future) some Status object if not ok or otherwise None
     private[skuber] def checkResponseStatus(response: HttpResponse): Future[Option[Status]] = {
       response.status.intValue match {
         case code if code < 300 =>
           if (log.isDebugEnabled())
             log.debug(s"[Skuber response: status = $code]")
           Future.successful(None)
         case code =>
           // a non-success or unexpected status returned - we should normally have a Status in the response body
           val statusFut: Future[Status] = Unmarshal(response).to[Status]
           statusFut map { status =>
             if (log.isInfoEnabled)
               log.info(s"[Skuber Response: Status returned for non-ok response = $status")
             Some(status)
           } recover { case ex =>
             if (log.isErrorEnabled)
               log.error(s"[Skuber response: could not read Status for non-ok response, exception : ${ex.getMessage}]")
             Some(Status(
               message = Some("Unexpected exception trying to unmarshal Kubernetes Status response due to non-ok response code"),
               details = Some(ex.getMessage)
             ))
           }
       }
     }
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

  def init()(
    implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer): RequestContext =
  {
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
      case Some(fileUrl) =>
        val path = java.nio.file.Paths.get(new URL(fileUrl).toURI)
        Configuration.parseKubeconfigFile(path).get
      case None => buildBaseConfigForURL(skuberUrlEnv)
    }
    init(config)
  }

  def init(config: Configuration)(
    implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer): RequestContext =
  {
    init(config.currentContext)
  }

  def init(k8sContext: Context, closeHook: Option[() => Unit] = None)(
    implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer) : RequestContext =
  {
    val sslContext = TLS.establishSSLContext(k8sContext)
    val theRequestAuth = HTTPRequestAuth.establishRequestAuth(k8sContext)
    sslContext foreach { ssl =>
      val httpsContext = ConnectionContext.https(ssl, None,Some(scala.collection.immutable.Seq("TLSv1.2", "TLSv1")), None, None)
      Http().setDefaultClientHttpsContext(httpsContext)
    }

    val theNamespaceName = k8sContext.namespace.name match {
      case "" => "default"
      case name => name
    }

    val requestMaker = (uri: Uri, method: HttpMethod) => HttpRequest(method = method, uri = uri)
    val requestInvoker = (request: HttpRequest) => Http().singleRequest(request)

    new RequestContext(requestMaker, requestInvoker, k8sContext.cluster.server, theRequestAuth,theNamespaceName, closeHook)
  }
}
