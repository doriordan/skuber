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
    ZStream.unwrapScoped {
      for
        queue <- Queue.unbounded[Take[Throwable, WebSocketMessage]]

        baseUrl       = URL.decode(req.url).getOrElse(URL.empty)
        urlWithParams = if req.queryParams.nonEmpty then
          baseUrl.copy(queryParams = QueryParams(req.queryParams.map { case (k, v) => k -> Chunk(v) }*))
        else baseUrl
        wsHeaders = Headers(
          req.headers.map { case (k, v) => Header.Custom(k, v) }.toList
        )

        app = WebSocketApp(Handler.fromFunctionZIO { (channel: WebSocketChannel) =>
          Promise.make[Nothing, Unit].flatMap { handshakeDone =>
            val receiveLoop = channel.receiveAll {
              case ChannelEvent.UserEventTriggered(ChannelEvent.UserEvent.HandshakeComplete) =>
                handshakeDone.succeed(()).unit
              case ChannelEvent.Read(WebSocketFrame.Binary(bytes)) =>
                queue.offer(Take.single(WebSocketMessage.Binary(bytes.toArray))).unit
              case ChannelEvent.Read(WebSocketFrame.Close(status, reason)) if status != 1000 =>
                val msg = reason.getOrElse(s"WebSocket closed with status $status")
                queue.offer(Take.fail(new Exception(msg))).unit
              case ChannelEvent.ExceptionCaught(cause) =>
                queue.offer(Take.fail(cause)).unit
              case ChannelEvent.Unregistered =>
                queue.offer(Take.end).unit
              case _ =>
                ZIO.unit
            }
            val sendLoop: ZIO[Any, Throwable, Unit] = stdin.fold(ZIO.unit) { stdinStream =>
              handshakeDone.await *> stdinStream.foreach { data =>
                channel.send(ChannelEvent.Read(WebSocketFrame.binary(Chunk.fromArray(data))))
              }
            }
            receiveLoop.zipParLeft(sendLoop)
          }
        }).withConfig(WebSocketConfig.default.subProtocol(Some("channel.k8s.io")))

        _ <- client.url(urlWithParams).addHeaders(wsHeaders).socket(app)
               .filterOrFail(_.status == Status.SwitchingProtocols)(
                 new Exception(s"WebSocket upgrade failed: expected 101 Switching Protocols")
               )
               .tapError(e => queue.offer(Take.fail(e)).ignore)
               .ensuring(queue.offer(Take.end).ignore)
               .forkScoped
      yield ZStream.fromQueue(queue).flattenTake
    }

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
