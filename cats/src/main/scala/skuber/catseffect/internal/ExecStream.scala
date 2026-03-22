package skuber.catseffect.internal

import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream
import skuber.catseffect.ExecOutput
import skuber.internal.{AuthInterceptor, HttpMethod, K8sRequest, UrlBuilder, WebSocketMessage}

private[catseffect] object ExecStream:

  private val StdinChannel: Byte = 0
  private val StdoutChannel: Byte = 1
  private val StderrChannel: Byte = 2

  def exec[F[_]: Async](
    backend: HttpBackend[F],
    clusterServer: String,
    namespace: String,
    auth: skuber.api.client.AuthInfo,
    podName: String,
    command: Seq[String],
    containerName: Option[String],
    stdin: Option[Stream[F, String]],
    tty: Boolean
  ): Stream[F, ExecOutput] =

    val url = UrlBuilder.execUrl(clusterServer, namespace, podName)

    val stdinFlag = if stdin.isDefined then Seq("stdin" -> "true") else Seq.empty
    val containerParam = containerName.map("container" -> _).toSeq
    val queryParams: Seq[(String, String)] =
      command.map("command" -> _) ++
      Seq("stdout" -> "true", "stderr" -> "true", s"tty" -> tty.toString) ++
      containerParam ++
      stdinFlag

    val req = K8sRequest(method = HttpMethod.Get, url = url, queryParams = queryParams)

    val stdinBytes: Option[Stream[F, Array[Byte]]] = stdin.map: s =>
      s.map: msg =>
        val msgBytes = msg.getBytes("UTF-8")
        val framed = new Array[Byte](msgBytes.length + 1)
        framed(0) = StdinChannel
        System.arraycopy(msgBytes, 0, framed, 1, msgBytes.length)
        framed

    Stream.eval(Async[F].executionContext.flatMap { ec =>
      Async[F].fromFuture(Async[F].delay(AuthInterceptor.addAuth(req, auth)(using ec)))
    }).flatMap: authedReq =>
      backend.websocket(authedReq, stdinBytes).collect:
        case WebSocketMessage.Binary(data) if data.length > 1 =>
          val channel = data(0)
          val payload = new String(data, 1, data.length - 1, "UTF-8")
          channel match
            case StdoutChannel => ExecOutput.Stdout(payload)
            case StderrChannel => ExecOutput.Stderr(payload)
            case other => ExecOutput.Stderr(s"[unknown channel $other] $payload")
