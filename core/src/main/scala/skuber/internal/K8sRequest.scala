package skuber.internal

import scala.concurrent.duration.FiniteDuration

sealed trait HttpMethod
object HttpMethod {
  case object Get    extends HttpMethod
  case object Post   extends HttpMethod
  case object Put    extends HttpMethod
  case object Delete extends HttpMethod
  case object Patch  extends HttpMethod
}

case class K8sRequest(
  method: HttpMethod,
  url: String,
  headers: Map[String, String] = Map.empty,
  body: Option[Array[Byte]] = None,
  queryParams: Seq[(String, String)] = Seq.empty,
  timeout: Option[FiniteDuration] = None
)

case class K8sResponse(
  statusCode: Int,
  body: Array[Byte],
  headers: Map[String, String] = Map.empty
)

sealed trait WebSocketMessage
object WebSocketMessage {
  case class Binary(data: Array[Byte]) extends WebSocketMessage
  case object Close extends WebSocketMessage
}
