package skuber.catseffect.internal

import cats.effect.Async
import cats.syntax.all.*
import fs2.Stream
import skuber.catseffect.ExecOutput

import java.net.URLEncoder

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

    val baseUrl = UrlBuilder.execUrl(clusterServer, namespace, podName)

    // Build query string manually since command can have repeated keys
    val queryParts = scala.collection.mutable.ArrayBuffer[String]()
    command.foreach(cmd => queryParts += s"command=${URLEncoder.encode(cmd, "UTF-8")}")
    queryParts += "stdout=true"
    queryParts += "stderr=true"
    queryParts += s"tty=$tty"
    containerName.foreach(c => queryParts += s"container=${URLEncoder.encode(c, "UTF-8")}")
    if stdin.isDefined then queryParts += "stdin=true"

    val fullUrl = s"$baseUrl?${queryParts.mkString("&")}"

    val req = K8sRequest(method = HttpMethod.Get, url = fullUrl)

    val stdinBytes: Option[Stream[F, Array[Byte]]] = stdin.map: s =>
      s.map: msg =>
        val msgBytes = msg.getBytes("UTF-8")
        val framed = new Array[Byte](msgBytes.length + 1)
        framed(0) = StdinChannel
        System.arraycopy(msgBytes, 0, framed, 1, msgBytes.length)
        framed

    Stream.eval(AuthInterceptor.addAuth[F](req, auth)).flatMap: authedReq =>
      backend.websocket(authedReq, stdinBytes).collect:
        case WebSocketMessage.Binary(data) if data.length > 1 =>
          val channel = data(0)
          val payload = new String(data, 1, data.length - 1, "UTF-8")
          channel match
            case StdoutChannel => ExecOutput.Stdout(payload)
            case StderrChannel => ExecOutput.Stderr(payload)
            case other => ExecOutput.Stderr(s"[unknown channel $other] $payload")
