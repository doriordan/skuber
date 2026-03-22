package skuber.zio.internal

import zio.*
import zio.stream.*
import skuber.api.client.AuthInfo
import skuber.internal.{HttpMethod, K8sRequest, UrlBuilder, WebSocketMessage}
import skuber.zio.ExecOutput

private[zio] object ExecStream:

  private val StdinChannel: Byte  = 0
  private val StdoutChannel: Byte = 1
  private val StderrChannel: Byte = 2

  def exec(
    backend: HttpBackend,
    clusterServer: String,
    namespace: String,
    auth: AuthInfo,
    podName: String,
    command: Seq[String],
    containerName: Option[String],
    stdin: Option[ZStream[Any, Nothing, String]],
    tty: Boolean
  ): ZStream[Any, Throwable, ExecOutput] =

    val url = UrlBuilder.execUrl(clusterServer, namespace, podName)
    val queryParams: Seq[(String, String)] =
      command.map("command" -> _) ++
      Seq("stdout" -> "true", "stderr" -> "true", "tty" -> tty.toString) ++
      containerName.map("container" -> _).toSeq ++
      (if stdin.isDefined then Seq("stdin" -> "true") else Seq.empty)

    val req = K8sRequest(HttpMethod.Get, url, queryParams = queryParams)

    val stdinBytes: Option[ZStream[Any, Nothing, Array[Byte]]] = stdin.map: s =>
      s.map: msg =>
        val msgBytes = msg.getBytes("UTF-8")
        val framed   = new Array[Byte](msgBytes.length + 1)
        framed(0) = StdinChannel
        java.lang.System.arraycopy(msgBytes, 0, framed, 1, msgBytes.length)
        framed

    ZStream.fromZIO(AuthInterceptor.addAuth(req, auth)).flatMap: authedReq =>
      backend.websocket(authedReq, stdinBytes).collect:
        case WebSocketMessage.Binary(data) if data.length > 1 =>
          val channel = data(0)
          val payload = new String(data, 1, data.length - 1, "UTF-8")
          channel match
            case StdoutChannel => ExecOutput.Stdout(payload)
            case StderrChannel => ExecOutput.Stderr(payload)
            case other         => ExecOutput.Stderr(s"[unknown channel $other] $payload")
