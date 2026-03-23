package skuber.zio.internal

import zio.*
import zio.stream.*
import play.api.libs.json.{Format, Json, Writes}
import skuber.api.client.*
import skuber.api.patch.{JsonMergePatchStrategy, JsonPatchStrategy, Patch, StrategicMergePatchStrategy}
import skuber.internal.{HttpMethod, K8sRequest, K8sResponse, UrlBuilder}
import skuber.json.format.deleteOptionsFmt
import skuber.json.format.apiobj.statusReads
import skuber.model.*
import skuber.api.client.K8SException
import skuber.zio.{ExecOutput, ZKubernetesClient}

private[zio] class ZKubernetesClientImpl(
  backend: HttpBackend,
  clusterServer: String,
  auth: AuthInfo,
  namespace: String
) extends ZKubernetesClient:

  private def executeRequest(req: K8sRequest): IO[K8SException, K8sResponse] =
    for
      authedReq <- AuthInterceptor.addAuth(req, auth)
        .mapError(e => new K8SException(Status(message = Some(e.getMessage), code = Some(0))))
      response  <- backend.request(authedReq)
        .mapError(e => new K8SException(Status(message = Some(e.getMessage), code = Some(0))))
      _         <- ZIO.logDebug(s"Response: ${response.statusCode} ${req.method} ${req.url}")
    yield response

  private def parseResponse[O](response: K8sResponse)(using Format[O]): IO[K8SException, O] =
    if response.statusCode >= 200 && response.statusCode < 300 then
      PlayJsonBridge.decode[O](response.body) match
        case Right(obj) => ZIO.succeed(obj)
        case Left(err)  => ZIO.fail(new K8SException(Status(
          message = Some(s"Failed to parse response: $err"),
          code = Some(response.statusCode))))
    else
      val status = PlayJsonBridge.decode[Status](response.body) match
        case Right(s) => s
        case Left(_)  => Status(message = Some(new String(response.body, "UTF-8")),
                                code = Some(response.statusCode))
      ZIO.fail(new K8SException(status))

  private def parseDeleteResponse(response: K8sResponse): IO[K8SException, Unit] =
    if response.statusCode >= 200 && response.statusCode < 300 then
      ZIO.unit
    else
      val status = PlayJsonBridge.decode[Status](response.body) match
        case Right(s) => s
        case Left(_)  => Status(message = Some(new String(response.body, "UTF-8")),
                                code = Some(response.statusCode))
      ZIO.fail(new K8SException(status))

  override def get[O <: ObjectResource](name: String)(using Format[O], ResourceDefinition[O]): IO[K8SException, O] =
    val rd  = summon[ResourceDefinition[O]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd, Some(name))
    executeRequest(K8sRequest(HttpMethod.Get, url)).flatMap(parseResponse[O])

  override def getOption[O <: ObjectResource](name: String)(using Format[O], ResourceDefinition[O]): IO[K8SException, Option[O]] =
    get[O](name).map(Some(_)).catchSome:
      case e if e.isNotFound => ZIO.succeed(None)

  override def create[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O]): IO[K8SException, O] =
    val rd  = summon[ResourceDefinition[O]]
    val ns  = if obj.metadata.namespace.nonEmpty then obj.metadata.namespace else namespace
    val url = UrlBuilder.resourceUrl(clusterServer, ns, rd)
    val body = PlayJsonBridge.encode(obj)
    executeRequest(K8sRequest(HttpMethod.Post, url, body = Some(body), headers = Map("Content-Type" -> "application/json"))).flatMap(parseResponse[O])

  override def update[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O]): IO[K8SException, O] =
    val rd   = summon[ResourceDefinition[O]]
    val ns   = if obj.metadata.namespace.nonEmpty then obj.metadata.namespace else namespace
    val url  = UrlBuilder.resourceUrl(clusterServer, ns, rd, Some(obj.name))
    val body = PlayJsonBridge.encode(obj)
    executeRequest(K8sRequest(HttpMethod.Put, url, body = Some(body), headers = Map("Content-Type" -> "application/json"))).flatMap(parseResponse[O])

  override def delete[O <: ObjectResource](name: String, gracePeriodSeconds: Int = -1)(using ResourceDefinition[O]): IO[K8SException, Unit] =
    val rd   = summon[ResourceDefinition[O]]
    val url  = UrlBuilder.resourceUrl(clusterServer, namespace, rd, Some(name))
    val (body, headers) = if gracePeriodSeconds >= 0 then
      val opts = DeleteOptions(gracePeriodSeconds = Some(gracePeriodSeconds))
      (Some(PlayJsonBridge.encode(opts)), Map("Content-Type" -> "application/json"))
    else (None, Map.empty[String, String])
    executeRequest(K8sRequest(HttpMethod.Delete, url, body = body, headers = headers)).flatMap(parseDeleteResponse)

  override def deleteWithOptions[O <: ObjectResource](name: String, options: DeleteOptions)(using ResourceDefinition[O]): IO[K8SException, Unit] =
    val rd   = summon[ResourceDefinition[O]]
    val url  = UrlBuilder.resourceUrl(clusterServer, namespace, rd, Some(name))
    val body = PlayJsonBridge.encode(options)
    executeRequest(K8sRequest(HttpMethod.Delete, url, body = Some(body), headers = Map("Content-Type" -> "application/json"))).flatMap(parseDeleteResponse)

  override def list[L <: KList[?]]()(using Format[L], ResourceDefinition[L]): IO[K8SException, L] =
    val rd  = summon[ResourceDefinition[L]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd)
    executeRequest(K8sRequest(HttpMethod.Get, url)).flatMap(parseResponse[L])

  override def listSelected[L <: KList[?]](labelSelector: LabelSelector)(using Format[L], ResourceDefinition[L]): IO[K8SException, L] =
    listWithOptions[L](ListOptions(labelSelector = Some(labelSelector)))

  override def listWithOptions[L <: KList[?]](options: ListOptions)(using Format[L], ResourceDefinition[L]): IO[K8SException, L] =
    val rd  = summon[ResourceDefinition[L]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd)
    executeRequest(K8sRequest(HttpMethod.Get, url, queryParams = options.asMap.toSeq)).flatMap(parseResponse[L])

  override def updateStatus[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O], HasStatusSubresource[O]): IO[K8SException, O] =
    val rd   = summon[ResourceDefinition[O]]
    val url  = UrlBuilder.statusUrl(clusterServer, namespace, rd, obj.name)
    val body = PlayJsonBridge.encode(obj)
    executeRequest(K8sRequest(HttpMethod.Put, url, body = Some(body), headers = Map("Content-Type" -> "application/json"))).flatMap(parseResponse[O])

  override def getScale[O <: ObjectResource](name: String)(using ResourceDefinition[O], Scale.SubresourceSpec[O]): IO[K8SException, Scale] =
    val rd  = summon[ResourceDefinition[O]]
    val url = UrlBuilder.scaleUrl(clusterServer, namespace, rd, name)
    given Format[Scale] = Scale.scaleFormat
    executeRequest(K8sRequest(HttpMethod.Get, url)).flatMap(parseResponse[Scale])

  override def updateScale[O <: ObjectResource](name: String, scale: Scale)(using ResourceDefinition[O], Scale.SubresourceSpec[O]): IO[K8SException, Scale] =
    val rd   = summon[ResourceDefinition[O]]
    val url  = UrlBuilder.scaleUrl(clusterServer, namespace, rd, name)
    given Format[Scale] = Scale.scaleFormat
    val body = PlayJsonBridge.encode(scale)
    executeRequest(K8sRequest(HttpMethod.Put, url, body = Some(body), headers = Map("Content-Type" -> "application/json"))).flatMap(parseResponse[Scale])

  override def patch[P <: Patch, O <: ObjectResource](name: String, patchData: P, namespace: Option[String] = None)(using Writes[P], Format[O], ResourceDefinition[O]): IO[K8SException, O] =
    val rd  = summon[ResourceDefinition[O]]
    val ns  = namespace.getOrElse(this.namespace)
    val url = UrlBuilder.resourceUrl(clusterServer, ns, rd, Some(name))
    val contentType = patchData.strategy match
      case StrategicMergePatchStrategy => "application/strategic-merge-patch+json"
      case JsonMergePatchStrategy      => "application/merge-patch+json"
      case JsonPatchStrategy           => "application/json-patch+json"
    val body = PlayJsonBridge.encode(patchData)
    executeRequest(K8sRequest(HttpMethod.Patch, url, body = Some(body), headers = Map("Content-Type" -> contentType))).flatMap(parseResponse[O])

  override def getServerAPIVersions: IO[K8SException, List[String]] =
    val req = K8sRequest(HttpMethod.Get, s"$clusterServer/api")
    executeRequest(req).flatMap: response =>
      if response.statusCode >= 200 && response.statusCode < 300 then
        ZIO.attempt {
          val json = Json.parse(response.body)
          (json \ "versions").as[List[String]]
        }.mapError(e => new K8SException(Status(
          message = Some(s"Failed to parse API versions: ${e.getMessage}"),
          code = Some(response.statusCode))))
      else
        val status = PlayJsonBridge.decode[Status](response.body) match
          case Right(s) => s
          case Left(_)  => Status(message = Some(new String(response.body, "UTF-8")),
                                  code = Some(response.statusCode))
        ZIO.fail(new K8SException(status))

  override def usingNamespace(newNamespace: String): ZKubernetesClient =
    new ZKubernetesClientImpl(backend, clusterServer, auth, newNamespace)

  override def watch[O <: ObjectResource](params: WatchParameters = WatchParameters())(using Format[O], ResourceDefinition[O]): ZStream[Any, K8SException, WatchEvent[O]] =
    skuber.zio.internal.WatchStream.watch[O](backend, clusterServer, namespace, auth, params)

  override def getPodLogStream(name: String, queryParams: Pod.LogQueryParams = Pod.LogQueryParams(), namespace: Option[String] = None): ZStream[Any, Throwable, Byte] =
    val ns  = namespace.getOrElse(this.namespace)
    val url = UrlBuilder.podLogUrl(clusterServer, ns, name)
    val req = K8sRequest(HttpMethod.Get, url, queryParams = queryParams.asMap.toSeq)
    ZStream.fromZIO(AuthInterceptor.addAuth(req, auth)).flatMap(backend.streamRequest)

  override def exec(podName: String, command: Seq[String], containerName: Option[String] = None, stdin: Option[ZStream[Any, Nothing, String]] = None, tty: Boolean = false): ZStream[Any, Throwable, ExecOutput] =
    ExecStream.exec(backend, clusterServer, namespace, auth, podName, command, containerName, stdin, tty)

private[zio] object ZKubernetesClientImpl:
  /** Builds a client using the [[zio.http.Client]] already present in the environment.
   *  The caller is responsible for ensuring the Client's lifecycle outlives this client. */
  def acquire(config: skuber.api.Configuration): ZIO[zio.http.Client, Throwable, ZKubernetesClient] =
    val context       = config.currentContext
    val clusterServer = context.cluster.server
    val auth          = context.authInfo
    val namespace     = context.namespace.name
    ZIO.serviceWith[zio.http.Client]: client =>
      val backend = ziohttp.ZioHttpBackend(client)
      new ZKubernetesClientImpl(backend, clusterServer, auth, namespace)
