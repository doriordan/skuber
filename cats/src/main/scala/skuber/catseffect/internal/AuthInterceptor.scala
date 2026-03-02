package skuber.catseffect.internal

import cats.effect.Async
import cats.syntax.all.*
import skuber.api.client.*
import java.util.Base64

private[catseffect] object AuthInterceptor:

  def addAuth[F[_]: Async](req: K8sRequest, auth: AuthInfo): F[K8sRequest] =
    auth match
      case NoAuth | _: CertAuth =>
        Async[F].pure(req)
      case BasicAuth(user, pwd) =>
        val encoded = Base64.getEncoder.encodeToString(s"$user:$pwd".getBytes("UTF-8"))
        Async[F].pure(req.copy(headers = req.headers + ("Authorization" -> s"Basic $encoded")))
      case tokenAuth: AccessTokenAuth =>
        Async[F].pure(req.copy(headers = req.headers + ("Authorization" -> s"Bearer ${tokenAuth.accessToken}")))
      case asyncAuth: AsyncAccessTokenAuth =>
        Async[F].fromFuture(Async[F].delay(asyncAuth.accessToken())).map { token =>
          req.copy(headers = req.headers + ("Authorization" -> s"Bearer $token"))
        }
      case null =>
        Async[F].pure(req)
