package skuber.zio.internal.ziohttp

import zio.*
import zio.http.*
import zio.stream.*
import skuber.internal.{HttpMethod, K8sRequest, K8sResponse, WebSocketMessage}
import skuber.zio.internal.HttpBackend

private[zio] class ZioHttpBackend(client: Client) extends HttpBackend:

  private val batchedClient: ZClient[Any, Any, Body, Throwable, Response] = client.batched

  override def request(req: K8sRequest): IO[Throwable, K8sResponse] =
    val zioReq = toZioRequest(req)
    batchedClient.request(zioReq).flatMap: response =>
      response.body.asArray.map: bodyBytes =>
        K8sResponse(
          statusCode = response.status.code,
          body = bodyBytes,
          headers = response.headers.iterator
            .map(h => h.headerName -> h.renderedValue)
            .toMap
        )

  override def streamRequest(req: K8sRequest): ZStream[Any, Throwable, Byte] =
    ZStream.unwrapScoped(
      client.request(toZioRequest(req)).map(_.body.asStream)
    )

  override def websocket(req: K8sRequest, stdin: Option[ZStream[Any, Nothing, Array[Byte]]]): ZStream[Any, Throwable, WebSocketMessage] =
    // WebSocket placeholder — implemented in integration test phase
    ZStream.die(new NotImplementedError("ZioHttpBackend.websocket: implement using zio-http 3.x WebSocket API"))

  private def toZioRequest(req: K8sRequest): Request =
    val method = req.method match
      case HttpMethod.Get    => Method.GET
      case HttpMethod.Post   => Method.POST
      case HttpMethod.Put    => Method.PUT
      case HttpMethod.Delete => Method.DELETE
      case HttpMethod.Patch  => Method.PATCH

    val uri = URL.decode(req.url).getOrElse(URL.empty)
    val uriWithParams =
      if req.queryParams.nonEmpty then
        uri.copy(queryParams = QueryParams(req.queryParams.map { case (k, v) => k -> Chunk(v) }*))
      else uri

    val baseHeaders = Headers(req.headers.map { case (k, v) => Header.Custom(k, v) }.toList)
    val body = req.body.fold(Body.empty)(Body.fromArray)

    Request(method = method, url = uriWithParams, headers = baseHeaders, body = body)
