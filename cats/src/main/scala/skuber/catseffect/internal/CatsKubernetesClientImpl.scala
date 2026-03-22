package skuber.catseffect.internal

import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream
import org.slf4j.LoggerFactory
import play.api.libs.json.{Format, Json, Writes}
import skuber.api.client.*
import skuber.api.patch.{Patch, StrategicMergePatchStrategy, JsonMergePatchStrategy, JsonPatchStrategy}
import skuber.catseffect.{CatsKubernetesClient, CatsWatcher, ExecOutput}
import skuber.internal.{AuthInterceptor, HttpMethod, K8sRequest, K8sResponse, UrlBuilder}
import skuber.json.format.deleteOptionsFmt
import skuber.json.format.apiobj.statusReads
import skuber.model.*

private[catseffect] class CatsKubernetesClientImpl[F[_]: Async](
  backend: HttpBackend[F],
  clusterServer: String,
  auth: AuthInfo,
  namespace: String,
  logConfig: LoggingConfig
) extends CatsKubernetesClient[F]:

  private val log = LoggerFactory.getLogger("skuber.api")
  private val F = Async[F]

  private def executeRequest(req: K8sRequest)(using lc: LoggingContext): F[K8sResponse] =
    if log.isDebugEnabled then
      log.debug(s"[${lc.output}] Request: ${req.method} ${req.url}")
    F.executionContext.flatMap { ec =>
      F.fromFuture(F.delay(AuthInterceptor.addAuth(req, auth)(using ec)))
    }.flatMap(backend.request).flatTap { response =>
      F.delay {
        if log.isDebugEnabled then
          log.debug(s"[${lc.output}] Response: ${response.statusCode} ${req.method} ${req.url}")
        if response.statusCode >= 500 && log.isWarnEnabled then
          val body = new String(response.body, "UTF-8").take(200)
          log.warn(s"[${lc.output}] Server error ${response.statusCode} for ${req.method} ${req.url}: $body")
      }
    }

  private def parseResponse[O](response: K8sResponse)(using Format[O]): Either[Status, O] =
    if response.statusCode >= 200 && response.statusCode < 300 then
      PlayJsonBridge.decode[O](response.body) match
        case Right(obj) => Right(obj)
        case Left(err) => Left(Status(message = Some(s"Failed to parse response: $err"), code = Some(response.statusCode)))
    else
      val status = PlayJsonBridge.decode[Status](response.body) match
        case Right(s) => s
        case Left(_) => Status(message = Some(new String(response.body, "UTF-8")), code = Some(response.statusCode))
      Left(status)

  private def parseDeleteResponse(response: K8sResponse): Either[Status, Unit] =
    if response.statusCode >= 200 && response.statusCode < 300 then
      Right(())
    else
      val status = PlayJsonBridge.decode[Status](response.body) match
        case Right(s) => s
        case Left(_) => Status(message = Some(new String(response.body, "UTF-8")), code = Some(response.statusCode))
      Left(status)

  override def get[O <: ObjectResource](name: String)(using Format[O], ResourceDefinition[O], LoggingContext): F[Either[Status, O]] =
    val rd = summon[ResourceDefinition[O]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd, Some(name))
    val req = K8sRequest(method = HttpMethod.Get, url = url)
    executeRequest(req).map(parseResponse[O])

  override def getOption[O <: ObjectResource](name: String)(using Format[O], ResourceDefinition[O], LoggingContext): F[Option[O]] =
    get[O](name).flatMap:
      case Right(obj) => F.pure(Some(obj))
      case Left(status) if status.code.contains(404) => F.pure(None)
      case Left(status) => F.raiseError(new K8SException(status))

  override def create[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O], LoggingContext): F[Either[Status, O]] =
    val rd = summon[ResourceDefinition[O]]
    val ns = if obj.metadata.namespace.nonEmpty then obj.metadata.namespace else namespace
    val url = UrlBuilder.resourceUrl(clusterServer, ns, rd)
    val body = PlayJsonBridge.encode(obj)
    val req = K8sRequest(method = HttpMethod.Post, url = url, body = Some(body), headers = Map("Content-Type" -> "application/json"))
    executeRequest(req).map(parseResponse[O])

  override def update[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O], LoggingContext): F[Either[Status, O]] =
    val rd = summon[ResourceDefinition[O]]
    val name = obj.name
    val ns = if obj.metadata.namespace.nonEmpty then obj.metadata.namespace else namespace
    val url = UrlBuilder.resourceUrl(clusterServer, ns, rd, Some(name))
    val body = PlayJsonBridge.encode(obj)
    val req = K8sRequest(method = HttpMethod.Put, url = url, body = Some(body), headers = Map("Content-Type" -> "application/json"))
    executeRequest(req).map(parseResponse[O])

  override def delete[O <: ObjectResource](name: String, gracePeriodSeconds: Int = -1)(using ResourceDefinition[O], LoggingContext): F[Either[Status, Unit]] =
    val rd = summon[ResourceDefinition[O]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd, Some(name))
    val body = if gracePeriodSeconds >= 0 then
      val options = DeleteOptions(gracePeriodSeconds = Some(gracePeriodSeconds))
      Some(PlayJsonBridge.encode(options))
    else
      None
    val headers = if body.isDefined then Map("Content-Type" -> "application/json") else Map.empty[String, String]
    val req = K8sRequest(method = HttpMethod.Delete, url = url, body = body, headers = headers)
    executeRequest(req).map(parseDeleteResponse)

  override def deleteWithOptions[O <: ObjectResource](name: String, options: DeleteOptions)(using ResourceDefinition[O], LoggingContext): F[Either[Status, Unit]] =
    val rd = summon[ResourceDefinition[O]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd, Some(name))
    val body = PlayJsonBridge.encode(options)
    val req = K8sRequest(method = HttpMethod.Delete, url = url, body = Some(body), headers = Map("Content-Type" -> "application/json"))
    executeRequest(req).map(parseDeleteResponse)

  override def list[L <: KList[?]]()(using Format[L], ResourceDefinition[L], LoggingContext): F[Either[Status, L]] =
    val rd = summon[ResourceDefinition[L]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd)
    val req = K8sRequest(method = HttpMethod.Get, url = url)
    executeRequest(req).map(parseResponse[L])

  override def listSelected[L <: KList[?]](labelSelector: LabelSelector)(using Format[L], ResourceDefinition[L], LoggingContext): F[Either[Status, L]] =
    listWithOptions[L](ListOptions(labelSelector = Some(labelSelector)))

  override def listWithOptions[L <: KList[?]](options: ListOptions)(using Format[L], ResourceDefinition[L], LoggingContext): F[Either[Status, L]] =
    val rd = summon[ResourceDefinition[L]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd)
    val req = K8sRequest(method = HttpMethod.Get, url = url, queryParams = options.asMap.toSeq)
    executeRequest(req).map(parseResponse[L])

  override def updateStatus[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O], HasStatusSubresource[O], LoggingContext): F[Either[Status, O]] =
    val rd = summon[ResourceDefinition[O]]
    val name = obj.name
    val url = UrlBuilder.statusUrl(clusterServer, namespace, rd, name)
    val body = PlayJsonBridge.encode(obj)
    val req = K8sRequest(method = HttpMethod.Put, url = url, body = Some(body), headers = Map("Content-Type" -> "application/json"))
    executeRequest(req).map(parseResponse[O])

  override def getScale[O <: ObjectResource](name: String)(using ResourceDefinition[O], Scale.SubresourceSpec[O], LoggingContext): F[Either[Status, Scale]] =
    val rd = summon[ResourceDefinition[O]]
    val url = UrlBuilder.scaleUrl(clusterServer, namespace, rd, name)
    val req = K8sRequest(method = HttpMethod.Get, url = url)
    given Format[Scale] = Scale.scaleFormat
    executeRequest(req).map(parseResponse[Scale])

  override def updateScale[O <: ObjectResource](name: String, scale: Scale)(using ResourceDefinition[O], Scale.SubresourceSpec[O], LoggingContext): F[Either[Status, Scale]] =
    val rd = summon[ResourceDefinition[O]]
    val url = UrlBuilder.scaleUrl(clusterServer, namespace, rd, name)
    given Format[Scale] = Scale.scaleFormat
    val body = PlayJsonBridge.encode(scale)
    val req = K8sRequest(method = HttpMethod.Put, url = url, body = Some(body), headers = Map("Content-Type" -> "application/json"))
    executeRequest(req).map(parseResponse[Scale])

  override def patch[P <: Patch, O <: ObjectResource](name: String, patchData: P, namespace: Option[String] = None)(using Writes[P], Format[O], ResourceDefinition[O], LoggingContext): F[Either[Status, O]] =
    val rd = summon[ResourceDefinition[O]]
    val ns = namespace.getOrElse(this.namespace)
    val url = UrlBuilder.resourceUrl(clusterServer, ns, rd, Some(name))
    val contentType = patchData.strategy match
      case StrategicMergePatchStrategy => "application/strategic-merge-patch+json"
      case JsonMergePatchStrategy => "application/merge-patch+json"
      case JsonPatchStrategy => "application/json-patch+json"
    val body = PlayJsonBridge.encode(patchData)
    val req = K8sRequest(method = HttpMethod.Patch, url = url, body = Some(body), headers = Map("Content-Type" -> contentType))
    executeRequest(req).map(parseResponse[O])

  override def watch[O <: ObjectResource](params: WatchParameters = WatchParameters())(using Format[O], ResourceDefinition[O], LoggingContext): Stream[F, Either[Status, WatchEvent[O]]] =
    skuber.catseffect.internal.WatchStream.watch[F, O](backend, clusterServer, namespace, auth, params)

  override def getWatcher[O <: ObjectResource]: CatsWatcher[F, O] =
    new CatsWatcherImpl[F, O](backend, clusterServer, namespace, auth)

  override def getPodLogStream(name: String, queryParams: Pod.LogQueryParams, namespace: Option[String])(using lc: LoggingContext): Stream[F, Byte] =
    val ns = namespace.getOrElse(this.namespace)
    val url = UrlBuilder.podLogUrl(clusterServer, ns, name)
    val req = K8sRequest(method = HttpMethod.Get, url = url, queryParams = queryParams.asMap.toSeq)
    if log.isDebugEnabled then
      log.debug(s"[${lc.output}] Streaming pod log: GET $url")
    Stream.eval(F.executionContext.flatMap { ec =>
      F.fromFuture(F.delay(AuthInterceptor.addAuth(req, auth)(using ec)))
    }).flatMap(backend.streamRequest)

  override def exec(podName: String, command: Seq[String], containerName: Option[String], stdin: Option[Stream[F, String]], tty: Boolean)(using lc: LoggingContext): Stream[F, ExecOutput] =
    if log.isDebugEnabled then
      log.debug(s"[${lc.output}] Exec in pod $podName: ${command.mkString(" ")}")
    ExecStream.exec[F](backend, clusterServer, namespace, auth, podName, command, containerName, stdin, tty)

  override def usingNamespace(newNamespace: String): CatsKubernetesClient[F] =
    new CatsKubernetesClientImpl[F](backend, clusterServer, auth, newNamespace, logConfig)

  override def getServerAPIVersions(using LoggingContext): F[Either[Status, List[String]]] =
    val url = s"$clusterServer/api"
    val req = K8sRequest(method = HttpMethod.Get, url = url)
    executeRequest(req).map: response =>
      if response.statusCode >= 200 && response.statusCode < 300 then
        try
          val json = Json.parse(response.body)
          val versions = (json \ "versions").as[List[String]]
          Right(versions)
        catch
          case e: Exception =>
            Left(Status(message = Some(s"Failed to parse API versions: ${e.getMessage}"), code = Some(response.statusCode)))
      else
        val status = PlayJsonBridge.decode[Status](response.body) match
          case Right(s) => s
          case Left(_) => Status(message = Some(new String(response.body, "UTF-8")), code = Some(response.statusCode))
        Left(status)
