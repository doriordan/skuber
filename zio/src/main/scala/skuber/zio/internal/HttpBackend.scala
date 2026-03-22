package skuber.zio.internal

import zio.*
import zio.stream.*
import skuber.internal.{K8sRequest, K8sResponse, WebSocketMessage}

private[zio] trait HttpBackend:
  def request(req: K8sRequest): IO[Throwable, K8sResponse]
  def streamRequest(req: K8sRequest): ZStream[Any, Throwable, Byte]
  def websocket(req: K8sRequest,
    stdin: Option[ZStream[Any, Nothing, Array[Byte]]] = None
  ): ZStream[Any, Throwable, WebSocketMessage]
