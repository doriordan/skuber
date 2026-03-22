package skuber.zio.internal

import zio.*
import skuber.api.client.AuthInfo
import skuber.internal.{AuthInterceptor => CoreAuthInterceptor, K8sRequest}

private[zio] object AuthInterceptor:

  def addAuth(req: K8sRequest, auth: AuthInfo): IO[Throwable, K8sRequest] =
    ZIO.fromFuture(ec => CoreAuthInterceptor.addAuth(req, auth)(using ec))
