package skuber.catseffect.internal

import fs2.Stream

private[catseffect] trait HttpBackend[F[_]]:
  def request(req: K8sRequest): F[K8sResponse]
  def streamRequest(req: K8sRequest): Stream[F, Byte]
  def websocket(req: K8sRequest, stdin: Option[Stream[F, Array[Byte]]] = None): Stream[F, WebSocketMessage]
