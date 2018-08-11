package skuber.api

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.SystemProperties
import scala.util.{Failure, Success, Try}
import java.net.URL
import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}
import javax.net.ssl.SSLContext
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import skuber.api.security.{HTTPRequestAuth, TLS}
import skuber.json.PlayJsonSupportForAkkaHttp._
import skuber.json.format._
import skuber.json.format.apiobj._
import skuber._
import skuber.api.WatchSource.Start

import scala.concurrent.duration._

/**
 * @author David O'Riordan
 */
package object client {

  type Pool[T] = Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed]

  final val sysProps = new SystemProperties

  // Certificates and keys can be specified in configuration either as paths to files or embedded PEM data
  type PathOrData = Either[String,Array[Byte]]

   // K8S client API classes
   final val defaultApiServerURL = "http://localhost:8080"

  // Patch content type(s)
  final val `application/merge-patch+json`: MediaType.WithFixedCharset =
    MediaType.customWithFixedCharset("application", "merge-patch+json", HttpCharsets.`UTF-8`)

  case class Cluster(
     apiVersion: String = "v1",
     server: String = defaultApiServerURL,
     insecureSkipTLSVerify: Boolean = false,
     certificateAuthority: Option[PathOrData] = None
   )

   case class Context(
     cluster: Cluster = Cluster(),
     authInfo: AuthInfo = NoAuth,
     namespace: Namespace = Namespace.default
   )

   sealed trait AuthInfo

   sealed trait AccessTokenAuth extends AuthInfo {
     def accessToken: String
   }

   object NoAuth extends AuthInfo {
     override def toString: String = "NoAuth"
   }

   final case class BasicAuth(userName: String, password: String) extends AuthInfo {
     override def toString: String = s"${getClass.getSimpleName}(userName=$userName,password=<redacted>)"
   }

   final case class TokenAuth(token: String) extends AccessTokenAuth {

     override def accessToken: String = token

     override def toString: String = s"${getClass.getSimpleName}(token=<redacted>)"
   }

   final case class CertAuth(clientCertificate: PathOrData, clientKey: PathOrData, user: Option[String]) extends AuthInfo {
     override def toString: String = StringBuilder.newBuilder
       .append(getClass.getSimpleName)
       .append("(")
       .append {
         clientCertificate match {
           case Left(certPath: String) => "clientCertificate=" + certPath + " "
           case Right(_) => "clientCertificate=<PEM masked> "
         }
       }
       .append {
         clientKey match {
           case Left(certPath: String) => "clientKey=" + certPath + " "
           case Right(_) => "clientKey=<PEM masked> "
         }
       }
       .append("userName=")
       .append(user.getOrElse(""))
       .append(" )")
       .mkString
   }

   sealed trait AuthProviderAuth extends AccessTokenAuth {
     def name: String
   }

   // 'jwt' supports an oidc id token per https://kubernetes.io/docs/admin/authentication/#option-1---oidc-authenticator
   // - but does not yet support token refresh
   final case class OidcAuth(idToken: String) extends AuthProviderAuth {
     override val name = "oidc"

     override def accessToken: String = idToken

     override def toString = """OidcAuth(idToken=<redacted>)"""
   }

   final case class GcpAuth private(private val config: GcpConfiguration) extends AuthProviderAuth {
     override val name = "gcp"

     @volatile private var refresh: GcpRefresh = new GcpRefresh(config.accessToken, config.expiry)

     def refreshGcpToken(): GcpRefresh = {
       val output = config.cmd.execute()
       Json.parse(output).as[GcpRefresh]
     }

     def accessToken: String = this.synchronized {
       if(refresh.expired)
         refresh = refreshGcpToken()

       refresh.accessToken
     }

     override def toString =
       """GcpAuth(accessToken=<redacted>)""".stripMargin
   }

   final private[client] case class GcpRefresh(accessToken: String, expiry: Instant) {
     def expired: Boolean = Instant.now.isAfter(expiry.minusSeconds(20))
   }

  private[client] object GcpRefresh {
    implicit val gcpRefreshReads: Reads[GcpRefresh] = (
      (JsPath \ "credential" \ "access_token").read[String] and
      (JsPath \ "credential" \ "token_expiry").read[Instant]
    )(GcpRefresh.apply _)
  }

   final case class GcpConfiguration(accessToken: String, expiry: Instant, cmd: GcpCommand)

   final case class GcpCommand(cmd: String, args: String) {
     import scala.sys.process._
     def execute(): String = s"$cmd $args".!!
   }

   object GcpAuth {
     def apply(accessToken: String, expiry: Instant, cmdPath: String, cmdArgs: String): GcpAuth =
       new GcpAuth(
         GcpConfiguration(
           accessToken = accessToken,
           expiry = expiry,
           GcpCommand(cmdPath, cmdArgs)
         )
       )
   }

   // for use with the Watch command
   case class WatchEvent[T <: ObjectResource](_type: EventType.Value, _object: T)
   object EventType extends Enumeration {
      type EventType = Value
      val ADDED,MODIFIED,DELETED,ERROR = Value
   }

   private def loggingEnabled(logEventType: String, fallback: Boolean) : Boolean= {
     sysProps.get(s"skuber.log.$logEventType").map(_ => true).getOrElse(fallback)
   }

   // This class offers a fine-grained choice over events to be logged by the API (applicable only if INFO level is enabled)
   case class LoggingConfig(
     logConfiguration: Boolean=loggingEnabled("config", true), // outputs configuration on initialisation)
     logRequestBasic: Boolean=loggingEnabled("request", true), // logs method and URL for request
     logRequestBasicMetadata: Boolean=loggingEnabled("request.metadata", false), // logs key resource metadata information if available
     logRequestFullObjectResource: Boolean=loggingEnabled("request.object.full", false), // outputs full object resource if available
     logResponseBasic: Boolean=loggingEnabled("response", true), // logs basic response info (status code)
     logResponseBasicMetadata: Boolean=loggingEnabled("response.metadata", false), // logs some basic metadata from the returned resource, if available
     logResponseFullObjectResource: Boolean=loggingEnabled("response.object.full", false), // outputs full received object resource, if available
     logResponseListSize: Boolean=loggingEnabled("response.list.size", false), // logs size of any returned list resource
     logResponseListNames: Boolean=loggingEnabled("response.list.names", false), // logs list of names of items in any returned list resource
     logResponseFullListResource: Boolean= loggingEnabled("response.list.full", false) // outputs full contained object resources in list resources
   )

   trait LoggingContext {
     def output: String
   }
   case class RequestLoggingContext(requestId: String) extends LoggingContext {
     def output=s"{ reqId=$requestId} }"
   }
   object RequestLoggingContext {
     def apply(): RequestLoggingContext=new RequestLoggingContext(UUID.randomUUID.toString)
   }

   class RequestContext(val requestMaker: (Uri, HttpMethod)  => HttpRequest,
                        val requestInvoker: (HttpRequest, Boolean) => Future[HttpResponse],
                        val clusterServer: String,
                        val requestAuth: AuthInfo,
                        val namespaceName: String,
                        val watchContinuouslyRequestTimeout: Duration,
                        val watchContinuouslyIdleTimeout: Duration,
                        val watchPoolIdleTimeout: Duration,
                        val sslContext: Option[SSLContext],
                        val logConfig: LoggingConfig,
                        val closeHook: Option[() => Unit])
      (implicit val actorSystem: ActorSystem, val materializer: Materializer, val executionContext: ExecutionContext) {

     val log = Logging.getLogger(actorSystem, "skuber.api")

     private val clusterServerUri = Uri(clusterServer)

     private var isClosed = false

     private[skuber] def invoke(request: HttpRequest, watch: Boolean = false)(implicit lc: LoggingContext): Future[HttpResponse] = {
       if (isClosed) {
         logError("Attempt was made to invoke request on closed API request context")
         throw new IllegalStateException("Request context has been closed")
       }
       logInfo(logConfig.logRequestBasic, s"about to send HTTP request: ${request.method.value} ${request.uri.toString}")
       val responseFut = requestInvoker(request, watch)
       responseFut onComplete {
         case Success(response) => logInfo(logConfig.logResponseBasic,s"received response with HTTP status ${response.status.intValue()}")
         case Failure(ex) => logError("HTTP request resulted in an unexpected exception",ex)
       }
       responseFut
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
         rd.spec.defaultVersion,
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

     private[skuber] def logInfo(enabledLogEvent: Boolean, msg: => String)(implicit lc: LoggingContext) =
     {
       if (log.isInfoEnabled && enabledLogEvent) {
         log.info(s"[ ${lc.output} - ${msg}]")
       }
     }

     private[skuber] def logInfoOpt(enabledLogEvent: Boolean, msgOpt: => Option[String])(implicit lc: LoggingContext) =
     {
       if (log.isInfoEnabled && enabledLogEvent) {
         msgOpt foreach { msg =>
           log.info(s"[ ${lc.output} - ${msg}]")
         }
       }
     }

     private[skuber] def logWarn(msg: String)(implicit lc: LoggingContext) =
     {
       log.error(s"[ ${lc.output} - $msg ]")
     }

     private[skuber] def logError(msg: String)(implicit lc: LoggingContext) =
     {
       log.error(s"[ ${lc.output} - $msg ]")
     }

     private[skuber] def logError(msg: String, ex: Throwable)(implicit lc: LoggingContext) =
     {
       log.error(ex, s"[ ${lc.output} - $msg ]")
     }

     private[skuber] def logDebug(msg : => String)(implicit lc: LoggingContext) = {
       if (log.isDebugEnabled)
         log.debug(s"[ ${lc.output} - $msg ]")
     }

     private[skuber] def logRequestObjectDetails[O <: ObjectResource](method: HttpMethod,resource: O)(implicit lc: LoggingContext) = {
       logInfoOpt(logConfig.logRequestBasicMetadata, {
           val name = resource.name
           val version = resource.metadata.resourceVersion
           method match {
             case HttpMethods.PUT | HttpMethods.PATCH => Some(s"Requesting update of resource: { name:$name, version:$version ... }")
             case HttpMethods.POST => Some(s"Requesting creation of resource: { name: $name ...}")
             case _ => None
           }
         }
       )
       logInfo(logConfig.logRequestFullObjectResource, s" Marshal and send: ${resource.toString}")
     }

     private[skuber] def logReceivedObjectDetails[O <: ObjectResource](resource: O)(implicit lc: LoggingContext) =
     {
       logInfo(logConfig.logResponseBasicMetadata, s" resource: { kind:${resource.kind} name:${resource.name} version:${resource.metadata.resourceVersion} ... }")
       logInfo(logConfig.logResponseFullObjectResource, s" received and parsed: ${resource.toString}")
     }

     private[skuber] def logReceivedListDetails[L <: ListResource[_]](result: L)(implicit lc: LoggingContext) =
     {
       logInfo(logConfig.logResponseBasicMetadata,s"received list resource of kind ${result.kind}")
       logInfo(logConfig.logResponseListSize,s"number of items in received list resource: ${result.items.size}")
       logInfo(logConfig.logResponseListNames, s"received ${result.kind} contains item(s): ${result.itemNames}]")
       logInfo(logConfig.logResponseFullListResource, s" Unmarshalled list resource: ${result.toString}")
     }

     private[skuber] def makeRequestReturningObjectResource[O <: ObjectResource](httpRequest: HttpRequest)(
       implicit fmt: Format[O], lc: LoggingContext): Future[O] =
     {
       for {
         httpResponse <- invoke(httpRequest)
         result <- toKubernetesResponse[O](httpResponse)
         _ = logReceivedObjectDetails(result)
       } yield result
     }

     private[skuber] def makeRequestReturningListResource[L <: ListResource[_]](httpRequest: HttpRequest)(
       implicit fmt: Format[L], lc: LoggingContext): Future[L] =
     {
       for {
         httpResponse <- invoke(httpRequest)
         result <- toKubernetesResponse[L](httpResponse)
         _ = logReceivedListDetails(result)
       } yield result
     }

     /**
       * Modify the specified K8S resource using a given HTTP method. The modified resource is returned.
       * The create, update and partiallyUpdate methods all call this, just passing different HTTP methods
       */
     private[skuber] def modify[O <: ObjectResource](method: HttpMethod)(obj: O)(
       implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] =
     {
       // if this is a POST we don't include the resource name in the URL
       val nameComponent: Option[String] = method match {
         case HttpMethods.POST => None
         case _ => Some(obj.name)
       }
       modify(method, obj, nameComponent)
     }

     private[skuber] def  modify[O <: ObjectResource](method: HttpMethod, obj: O, nameComponent: Option[String])(
       implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] =
     {
       logRequestObjectDetails(method, obj)
       val marshal = Marshal(obj)
       for {
         requestEntity <- marshal.to[RequestEntity]
         httpRequest = buildRequest(method, rd, nameComponent).withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
         newOrUpdatedResource <- makeRequestReturningObjectResource[O](httpRequest)
       } yield newOrUpdatedResource
     }

     def create[O <: ObjectResource](obj: O)(
       implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext=RequestLoggingContext()): Future[O] =
     {
       modify(HttpMethods.POST)(obj)
     }

     def update[O <: ObjectResource](obj: O)(
       implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext=RequestLoggingContext()): Future[O] =
     {
       modify(HttpMethods.PUT)(obj)
     }

     def updateStatus[O <: ObjectResource](obj: O)(implicit
       fmt: Format[O],
       rd: ResourceDefinition[O],
       lc: LoggingContext=RequestLoggingContext(),
       statusEv: HasStatusSubresource[O]): Future[O] =
     {
       val statusSubresourcePath=s"${obj.name}/status"
       modify(HttpMethods.PUT,obj,Some(statusSubresourcePath))
     }

     def getStatus[O <: ObjectResource](name: String)(implicit
       fmt: Format[O],
       rd: ResourceDefinition[O],
       lc: LoggingContext=RequestLoggingContext(),
       statusEv: HasStatusSubresource[O]): Future[O] =
     {
        _get[O](s"${name}/status")
     }

     def getNamespaceNames(implicit lc: LoggingContext=RequestLoggingContext()): Future[List[String]] =
     {
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
         (implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext=RequestLoggingContext()): Future[Map[String, L]] =
     {
       listByNamespace[L](rd)
     }

     private def listByNamespace[L <: ListResource[_]](rd: ResourceDefinition[_])
         (implicit fmt: Format[L],lc: LoggingContext): Future[Map[String, L]] =
     {
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
         (implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext=RequestLoggingContext()): Future[L] =
     {
       listInNamespace[L](theNamespace, rd)
     }

     private def listInNamespace[L <: ListResource[_]](theNamespace: String, rd: ResourceDefinition[_])
         (implicit fmt: Format[L], lc: LoggingContext): Future[L] =
     {
       val req = buildRequest(HttpMethods.GET, rd, None, namespace = theNamespace)
       makeRequestReturningListResource[L](req)
     }

     /*
      * List objects of specific resource kind in current namespace
      */
     def list[L <: ListResource[_]]()(
       implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext=RequestLoggingContext()): Future[L] =
     {
       _list[L](rd, None)
     }

     /*
      * Retrieve the list of objects of given type in the current namespace that match the supplied label selector
      */
     def listSelected[L <: ListResource[_]](labelSelector: LabelSelector)(
       implicit fmt: Format[L], rd: ResourceDefinition[L],lc: LoggingContext=RequestLoggingContext()): Future[L] =
     {
       _list[L](rd, Some(labelSelector))
     }

     private def _list[L <: ListResource[_]](rd: ResourceDefinition[_], maybeLabelSelector: Option[LabelSelector])(
       implicit fmt: Format[L], lc: LoggingContext=RequestLoggingContext()): Future[L] =
     {
       val queryOpt = maybeLabelSelector map { ls =>
         Uri.Query("labelSelector" -> ls.toString)
       }
       if (log.isDebugEnabled) {
         val lsInfo = maybeLabelSelector map { ls => s" with label selector '${ls.toString}'" } getOrElse ""
         logDebug(s"[List request: resources of kind '${rd.spec.names.kind}'${lsInfo}")
       }
       val req = buildRequest(HttpMethods.GET, rd, None, query = queryOpt)
       makeRequestReturningListResource[L](req)
     }

     def getOption[O <: ObjectResource](name: String)(
       implicit fmt: Format[O], rd: ResourceDefinition[O],lc: LoggingContext=RequestLoggingContext()): Future[Option[O]] =
     {
       _get[O](name) map { result =>
         Some(result)
       } recover {
         case ex: K8SException if ex.status.code.contains(StatusCodes.NotFound.intValue) => None
       }
     }

     def get[O <: ObjectResource](name: String)(
       implicit fmt: Format[O], rd: ResourceDefinition[O],lc: LoggingContext=RequestLoggingContext()): Future[O] = {
       _get[O](name)
     }

     def getInNamespace[O <: ObjectResource](name: String, namespace: String)(
       implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext=RequestLoggingContext()): Future[O] =
     {
       _get[O](name, namespace)
     }

     private[api] def _get[O <: ObjectResource](name: String, namespace: String = namespaceName)(
       implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] =
     {
       val req = buildRequest(HttpMethods.GET, rd, Some(name), namespace = namespace)
       makeRequestReturningObjectResource[O](req)
     }

     def delete[O <: ObjectResource](name: String, gracePeriodSeconds: Int = -1)(
       implicit rd: ResourceDefinition[O], lc: LoggingContext=RequestLoggingContext()): Future[Unit] =
     {
       val grace=if (gracePeriodSeconds >= 0) Some(gracePeriodSeconds) else None
       val options = DeleteOptions(gracePeriodSeconds = grace)
       deleteWithOptions[O](name, options)
     }

     def deleteWithOptions[O <: ObjectResource](name: String, options: DeleteOptions)(
       implicit rd: ResourceDefinition[O], lc: LoggingContext=RequestLoggingContext()): Future[Unit] =
     {
       val marshalledOptions = Marshal(options)
       for {
         requestEntity <- marshalledOptions.to[RequestEntity]
         request = buildRequest(HttpMethods.DELETE, rd, Some(name)).withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
         response <- invoke(request)
         _ <- checkResponseStatus(response)
         _ <- ignoreResponseBody(response)
       } yield ()
     }

     def getPodLogSource(name: String, queryParams: Pod.LogQueryParams, namespace: Option[String] = None)(
       implicit lc: LoggingContext=RequestLoggingContext()): Future[Source[ByteString, _]] =
     {
       val targetNamespace=namespace.getOrElse(this.namespaceName)
       val queryMap=queryParams.asMap
       val query: Option[Uri.Query] = if (queryMap.isEmpty) {
         None
       } else {
         Some(Uri.Query(queryMap))
       }
       val nameComponent=s"${name}/log"
       val rd = implicitly[ResourceDefinition[Pod]]
       val request=buildRequest(HttpMethods.GET, rd, Some(nameComponent), query, false, targetNamespace)
       invoke(request).map { response =>
         response.entity.dataBytes
       }
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
       implicit fmt: Format[O], rd: ResourceDefinition[O],lc: LoggingContext=RequestLoggingContext()): Future[Source[WatchEvent[O], _]] =
     {
       Watch.events(this, name, sinceResourceVersion, bufSize)
     }

     // watch events on all objects of specified kind in current namespace
     def watchAll[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
       implicit fmt: Format[O], rd: ResourceDefinition[O],lc: LoggingContext=RequestLoggingContext()): Future[Source[WatchEvent[O], _]] =
     {
       Watch.eventsOnKind[O](this, sinceResourceVersion, bufSize)
     }

     def watchContinuously[O <: ObjectResource](obj: O)(
       implicit fmt: Format[O], rd: ResourceDefinition[O]): Source[WatchEvent[O], _] =
     {
       watchContinuously(obj.name)
     }

     def watchContinuously[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
       implicit fmt: Format[O], rd: ResourceDefinition[O],lc: LoggingContext=RequestLoggingContext()): Source[WatchEvent[O], _] =
     {
       WatchSource(this,
         buildLongPollingPool(),
         Some(name), sinceResourceVersion,
         watchContinuouslyRequestTimeout, bufSize
       )
     }

     def watchAllContinuously[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
       implicit fmt: Format[O], rd: ResourceDefinition[O],lc: LoggingContext=RequestLoggingContext()): Source[WatchEvent[O], _] =
     {
       WatchSource(this,
         buildLongPollingPool(),
         None, sinceResourceVersion,
         watchContinuouslyRequestTimeout, bufSize
       )
     }

     private def buildLongPollingPool[O <: ObjectResource]() = {
       LongPollingPool[Start[O]](
         clusterServerUri.scheme,
         clusterServerUri.authority.host.address(),
         clusterServerUri.effectivePort,
         watchPoolIdleTimeout,
         sslContext.map(new HttpsConnectionContext(_)),
         ClientConnectionSettings(actorSystem.settings.config).withIdleTimeout(watchContinuouslyIdleTimeout)
       )
     }

     // Operations on scale subresource
     // Scale subresource Only exists for certain resource types like RC, RS, Deployment, StatefulSet so only those types
     // define an implicit Scale.SubresourceSpec, which is required to be passed to these methods.
     def getScale[O <: ObjectResource](objName: String)(
       implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc: LoggingContext=RequestLoggingContext()) : Future[Scale] =
     {
       val req = buildRequest(HttpMethods.GET, rd, Some(objName+ "/scale"))
       makeRequestReturningObjectResource[Scale](req)
     }

     @deprecated("use getScale followed by updateScale instead")
     def scale[O <: ObjectResource](objName: String, count: Int)(
       implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc:LoggingContext=RequestLoggingContext()): Future[Scale] =
     {
       val scale = Scale(
         apiVersion = sc.apiVersion,
         metadata = ObjectMeta(name = objName, namespace = namespaceName),
         spec = Scale.Spec(replicas = count)
       )
       updateScale[O](objName, scale)
     }

     def updateScale[O <: ObjectResource](objName: String, scale: Scale)(
      implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc:LoggingContext=RequestLoggingContext()): Future[Scale] =
     {
       implicit val dispatcher=actorSystem.dispatcher
       val marshal = Marshal(scale)
       for {
         requestEntity <- marshal.to[RequestEntity]
         httpRequest = buildRequest(HttpMethods.PUT, rd, Some(s"${objName}/scale")).withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
         scaledResource <- makeRequestReturningObjectResource[Scale](httpRequest)
       } yield scaledResource
     }

     /**
      * Perform a Json merge patch on a resource
      * The patch is passed a String type which should contain the JSON patch formatted per https://tools.ietf.org/html/rfc7386
      * It is a String type instead of a JSON object in order to allow clients to use their own favourite JSON library to create the
      * patch, or alternatively to simply manually craft the JSON and insert it into a String.  Also patches are generally expected to be
      * relatively small, so storing the whole patch in memory should not be problematic.
      * It is thus the responsibility of the client to ensure that the `patch` parameter contains a valid JSON merge patch entity for the
      * targetted Kubernetes resource `obj`
      * @param obj The resource to update with the patch
      * @param patch A string containing the JSON patch entity
      * @return The patched resource (in a Future)
      */
     def jsonMergePatch[O <: ObjectResource](obj: O, patch: String)(
       implicit rd: ResourceDefinition[O], fmt: Format[O], lc:LoggingContext=RequestLoggingContext()): Future[O] =
     {
       val patchRequestEntity = HttpEntity.Strict(`application/merge-patch+json`, ByteString(patch))
       val httpRequest = buildRequest(HttpMethods.PATCH, rd, Some(obj.name)).withEntity(patchRequestEntity)
       makeRequestReturningObjectResource[O](httpRequest)
     }

     // get API versions supported by the cluster
     def getServerAPIVersions(implicit lc: LoggingContext=RequestLoggingContext()): Future[List[String]] = {
       val url = clusterServer + "/api"
       val noAuthReq = requestMaker(Uri(url), HttpMethods.GET)
       val request = HTTPRequestAuth.addAuth(noAuthReq, requestAuth)
       for {
         response <- invoke(request)
         apiVersionResource <- toKubernetesResponse[APIVersions](response)
       } yield apiVersionResource.versions
     }

     def close: Unit =
     {
       isClosed = true
       closeHook foreach {
         _ ()
       } // invoke the specified close hook if specified
     }

     /*
      * Lightweight switching of namespace for applications that need to access multiple namespaces on same cluster
      * and using same credentials and other configuration.
      */
     def usingNamespace(newNamespace: String): RequestContext =
       new RequestContext(requestMaker, requestInvoker, clusterServer, requestAuth,
         newNamespace, watchContinuouslyRequestTimeout,  watchContinuouslyIdleTimeout,
         watchPoolIdleTimeout, sslContext, logConfig, closeHook
       )

     private[skuber] def toKubernetesResponse[T](response: HttpResponse)(implicit reader: Reads[T], lc: LoggingContext): Future[T] =
     {
       val statusOptFut = checkResponseStatus(response)
       statusOptFut flatMap {
         case Some(status) =>
           throw new K8SException(status)
         case None =>
           try {
             Unmarshal(response).to[T]
           }
           catch {
             case ex: Exception =>
               logError("Unable to unmarshal resource from response", ex)
               throw new K8SException(Status(message = Some("Error unmarshalling resource from response"), details = Some(ex.getMessage)))
           }
       }
     }

     // check for non-OK status, returning (in a Future) some Status object if not ok or otherwise None
     private[skuber] def checkResponseStatus(response: HttpResponse)(implicit lc: LoggingContext): Future[Option[Status]] =
     {
       response.status.intValue match {
         case code if code < 300 =>
           Future.successful(None)
         case code =>
           // a non-success or unexpected status returned - we should normally have a Status in the response body
           val statusFut: Future[Status] = Unmarshal(response).to[Status]
           statusFut map { status =>
             if (log.isInfoEnabled)
               log.info(s"[Response: non-ok status returned - $status")
             Some(status)
           } recover { case ex =>
             if (log.isErrorEnabled)
               log.error(s"[Response: could not read Status for non-ok response, exception : ${ex.getMessage}]")
             Some(Status(
               code = Some(response.status.intValue),
               message = Some("Non-ok response and unable to parse Status from response body to get further details"),
               details = Some(ex.getMessage)
             ))
           }
       }
     }

     /**
       * Discards the response
       * This is for requests (e.g. delete) for which we normally have no interest in the response body, but Akka Http
       * requires us to drain it anyway
       * (see https://doc.akka.io/docs/akka-http/current/scala/http/implications-of-streaming-http-entity.html)
       * @param response the Http Response that we need to drain
       * @return A Future[Unit] that will be set to Success or Failure depending on outcome of draining
       */
     private def ignoreResponseBody(response: HttpResponse): Future[Unit] = {
         response.discardEntityBytes().future.map(done => ())
     }
   }

  // Status will usually be returned by Kubernetes when an error occurs with a request
  case class Status(
    apiVersion: String = "v1",
    kind: String = "Status",
    metadata: ListMeta = ListMeta(),
    status: Option[String] = None,
    message: Option[String]= None,
    reason: Option[String] = None,
    details: Option[Any] = None,
    code: Option[Int] = None  // HTTP status code
  )

  class K8SException(val status: Status) extends RuntimeException (status.toString) // we throw this when we receive a non-OK response

  def init()(implicit actorSystem: ActorSystem, materializer: Materializer): RequestContext = {
    // Initialising without explicit Kubernetes Configuration.  If SKUBER_URL environment variable is set then it
    // is assumed to point to a kubectl proxy running at the URL specified so we configure to use that, otherwise if not set
    // then we try to configure from a kubeconfig file located as follows:
    //
    // If SKUBER_CONFIG is set, then use the path specified e.g.
    // `SKUBER_CONFIG=file:///etc/kubeconfig` will load config from the file `/etc/kubeconfig`
    // Note: `SKUBER_CONFIG=file` is a special case: it will load config from the default kubeconfig path `$HOME/.kube/config`
    // Note: `SKUBER_CONFIG=proxy` is also special: it configures skuber to connect via localhost:8080
    // If SKUBER_CONFIG is also not set, then we next fallback to the standard Kubernetes environment variable KUBECONFIG
    // If neither of these is set, then finally it tries the standard kubeconfig file location `$HOME/.kube/config` before
    // giving up and throwing an exception
    //
    init(defaultK8sConfig, defaultAppConfig)
  }

  def init(config: Configuration)(implicit actorSystem: ActorSystem, materializer: Materializer): RequestContext = {
    init(config.currentContext, LoggingConfig(), None, defaultAppConfig)
  }

  def init(appConfig: Config)(implicit actorSystem: ActorSystem, materializer: Materializer): RequestContext = {
    init(defaultK8sConfig.currentContext, LoggingConfig(), None, appConfig)
  }

  def init(config: Configuration, appConfig: Config)(implicit actorSystem: ActorSystem, materializer: Materializer): RequestContext = {
    init(config.currentContext, LoggingConfig(), None, appConfig)
  }

  def init(k8sContext: Context, logConfig: LoggingConfig, closeHook: Option[() => Unit] = None)
      (implicit actorSystem: ActorSystem, materializer: Materializer): RequestContext = {
    init(k8sContext, logConfig, closeHook, defaultAppConfig)
  }

  def init(k8sContext: Context, logConfig: LoggingConfig, closeHook: Option[() => Unit], appConfig: Config)
      (implicit actorSystem: ActorSystem, materializer: Materializer): RequestContext =
  {
    appConfig.checkValid(ConfigFactory.defaultReference(), "skuber")

    def getSkuberConfig[T](key: String, fromConfig: String => Option[T], default: T): T = {
      val skuberConfigKey = s"skuber.$key"
      if (appConfig.getIsNull(skuberConfigKey)) {
        default
      } else {
        fromConfig(skuberConfigKey) match {
          case None => default
          case Some(t) => t
        }
      }
    }

    def dispatcherFromConfig(configKey: String): Option[ExecutionContext] = if (appConfig.getString(configKey).isEmpty) {
      None
    } else {
      Some(actorSystem.dispatchers.lookup(appConfig.getString(configKey)))
    }
    implicit val dispatcher: ExecutionContext = getSkuberConfig("akka.dispatcher", dispatcherFromConfig, actorSystem.dispatcher)

    def durationFomConfig(configKey: String): Option[Duration] = Some(Duration.fromNanos(appConfig.getDuration(configKey).toNanos))
    val watchIdleTimeout: Duration = getSkuberConfig("watch.idle-timeout", durationFomConfig, Duration.Inf)

    val watchContinuouslyRequestTimeout: Duration = getSkuberConfig("watch-continuously.request-timeout", durationFomConfig, 30.seconds)
    val watchContinuouslyIdleTimeout: Duration = getSkuberConfig("watch-continuously.idle-timeout", durationFomConfig, 60.seconds)
    val watchPoolIdleTimeout: Duration = getSkuberConfig("watch-continuously.pool-idle-timeout", durationFomConfig, 60.seconds)

    //The watch idle timeout needs to be greater than watch api request timeout
    require(watchContinuouslyIdleTimeout > watchContinuouslyRequestTimeout)

    if (logConfig.logConfiguration) {
      val log = Logging.getLogger(actorSystem, "skuber.api")
      log.info("Using following context for connecting to Kubernetes cluster: {}", k8sContext)
    }

    val sslContext = TLS.establishSSLContext(k8sContext)
    sslContext foreach { ssl =>
      val httpsContext = ConnectionContext.https(ssl, None,Some(scala.collection.immutable.Seq("TLSv1.2", "TLSv1")), None, None)
      Http().setDefaultClientHttpsContext(httpsContext)
    }

    val theNamespaceName = k8sContext.namespace.name match {
      case "" => "default"
      case name => name
    }

    val requestMaker = (uri: Uri, method: HttpMethod) => HttpRequest(method = method, uri = uri)

    val defaultClientSettings = ConnectionPoolSettings(actorSystem.settings.config)
    val watchConnectionSettings = defaultClientSettings.connectionSettings.withIdleTimeout(watchIdleTimeout)
    val watchSettings = defaultClientSettings.withConnectionSettings(watchConnectionSettings)

    val requestInvoker = (request: HttpRequest, watch: Boolean) => {
      if (!watch)
        Http().singleRequest(request)
      else
        Http().singleRequest(request, settings = watchSettings)
    }

    new RequestContext(
      requestMaker, requestInvoker, k8sContext.cluster.server, k8sContext.authInfo,
      theNamespaceName, watchContinuouslyRequestTimeout, watchContinuouslyIdleTimeout,
      watchPoolIdleTimeout, sslContext, logConfig, closeHook
    )
  }

  def defaultK8sConfig: Configuration = {
    import java.nio.file.Paths

    val skuberUrlOverride = sys.env.get("SKUBER_URL")
    skuberUrlOverride match {
      case Some(url) =>
        Configuration.useProxyAt(url)
      case None =>
        val skuberConfigEnv = sys.env.get("SKUBER_CONFIG")
        skuberConfigEnv match {
          case Some(conf) if conf == "file" =>
            Configuration.parseKubeconfigFile().get // default kubeconfig location
          case Some(conf) if conf == "proxy" =>
            Configuration.useLocalProxyDefault
          case Some(fileUrl) =>
            val path = Paths.get(new URL(fileUrl).toURI)
            Configuration.parseKubeconfigFile(path).get
          case None =>
            // try KUBECONFIG
            val kubeConfigEnv = sys.env.get("KUBECONFIG")
            kubeConfigEnv.map { kc =>
              Configuration.parseKubeconfigFile(Paths.get(kc))
            }.getOrElse {
              // Try to get config from a running pod
              // if that is not set then use default kubeconfig location
              Configuration.useRunningPod.orElse(
                Configuration.parseKubeconfigFile()
              )
            }.get
        }
    }
  }

  private def defaultAppConfig: Config = ConfigFactory.load()
}
