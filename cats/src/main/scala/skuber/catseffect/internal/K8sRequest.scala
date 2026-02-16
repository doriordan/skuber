package skuber.catseffect.internal

import scala.concurrent.duration.FiniteDuration

private[catseffect] enum HttpMethod:
  case Get, Post, Put, Delete, Patch

private[catseffect] case class K8sRequest(
  method: HttpMethod,
  url: String,
  headers: Map[String, String] = Map.empty,
  body: Option[Array[Byte]] = None,
  queryParams: Map[String, String] = Map.empty,
  timeout: Option[FiniteDuration] = None
)

private[catseffect] case class K8sResponse(
  statusCode: Int,
  body: Array[Byte],
  headers: Map[String, String] = Map.empty
)

private[catseffect] enum WebSocketMessage:
  case Binary(data: Array[Byte])
  case Close
