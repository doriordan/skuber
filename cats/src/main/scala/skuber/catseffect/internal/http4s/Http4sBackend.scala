package skuber.catseffect.internal.http4s

import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream
import org.http4s.*
import org.http4s.client.Client
import org.http4s.client.websocket.{WSClient, WSFrame, WSRequest}
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString
import skuber.catseffect.internal.*
import skuber.internal.{HttpMethod, K8sRequest, K8sResponse, WebSocketMessage}

private[catseffect] class Http4sBackend[F[_]: Async](
  client: Client[F],
  wsClient: WSClient[F]
) extends HttpBackend[F]:

  override def request(req: K8sRequest): F[K8sResponse] =
    val http4sReq = toHttp4sRequest(req)
    client.run(http4sReq).use { response =>
      response.body.compile.to(Array).map { bodyBytes =>
        K8sResponse(
          statusCode = response.status.code,
          body = bodyBytes,
          headers = response.headers.headers.map(h => h.name.toString -> h.value).toMap
        )
      }
    }

  override def streamRequest(req: K8sRequest): Stream[F, Byte] =
    val http4sReq = toHttp4sRequest(req)
    Stream.resource(client.run(http4sReq)).flatMap(_.body)

  override def websocket(req: K8sRequest, stdin: Option[Stream[F, Array[Byte]]]): Stream[F, WebSocketMessage] =
    val baseWsUri = Uri.unsafeFromString(req.url.replaceFirst("^http", "ws"))
    val wsUri = if req.queryParams.nonEmpty then
      import org.http4s.Query
      baseWsUri.copy(query = Query.fromVector(
        req.queryParams.map { case (k, v) => k -> Some(v) }.toVector
      ))
    else baseWsUri
    val headers = Headers(
      req.headers.map { case (k, v) => Header.Raw(CIString(k), v) }.toList
        :+ Header.Raw(CIString("Sec-WebSocket-Protocol"), "channel.k8s.io")
    )
    Stream.resource(wsClient.connectHighLevel(WSRequest(wsUri, headers, Method.GET))).flatMap { (conn: org.http4s.client.websocket.WSConnectionHighLevel[F]) =>
      val receive: Stream[F, WebSocketMessage] = conn.receiveStream.collect {
        case WSFrame.Binary(data, _) => WebSocketMessage.Binary(data.toArray)
      }

      stdin match
        case Some(input) =>
          val send = input
            .map(data => WSFrame.Binary(scodec.bits.ByteVector(data)))
            .through(conn.sendPipe)
          receive.concurrently(send)
        case None =>
          receive
    }.handleErrorWith {
      // JDK websocket can throw IOException when sending close frame on an already-closed connection.
      // In this specific case we should not surface the exception to the application
      case e: java.io.IOException if e.getMessage == "closed output" => Stream.empty
      case e => Stream.raiseError(e)
    }

  private def toHttp4sRequest(req: K8sRequest): Request[F] =
    val method = req.method match
      case HttpMethod.Get => Method.GET
      case HttpMethod.Post => Method.POST
      case HttpMethod.Put => Method.PUT
      case HttpMethod.Delete => Method.DELETE
      case HttpMethod.Patch => Method.PATCH

    val baseUri = Uri.unsafeFromString(req.url)
    val uri = if req.queryParams.nonEmpty then
      import org.http4s.Query
      baseUri.copy(query = Query.fromVector(
        req.queryParams.map { case (k, v) => k -> Some(v) }.toVector
      ))
    else baseUri

    val headers = Headers(req.headers.map { case (k, v) =>
      Header.Raw(CIString(k), v)
    }.toList)

    val base = Request[F](method = method, uri = uri, headers = headers)
    req.body match
      case Some(bytes) =>
        val contentType = req.headers.get("Content-Type")
          .flatMap(ct => MediaType.parse(ct).toOption)
          .map(mt => `Content-Type`(mt))
          .getOrElse(`Content-Type`(MediaType.application.json))
        base.withEntity(bytes).putHeaders(contentType)
      case None => base
