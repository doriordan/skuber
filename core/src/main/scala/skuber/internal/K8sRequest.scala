// core/src/main/scala/skuber/internal/K8sRequest.scala
package skuber.internal

import scala.concurrent.duration.FiniteDuration

enum HttpMethod:
  case Get, Post, Put, Delete, Patch

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

enum WebSocketMessage:
  case Binary(data: Array[Byte])
  case Close
