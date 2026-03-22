package skuber.internal

import skuber.api.client._
import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}

object AuthInterceptor {

  def addAuth(req: K8sRequest, auth: AuthInfo)(implicit ec: ExecutionContext): Future[K8sRequest] =
    auth match {
      case NoAuth | _: CertAuth =>
        Future.successful(req)
      case BasicAuth(user, pwd) =>
        val encoded = Base64.getEncoder.encodeToString(s"$user:$pwd".getBytes("UTF-8"))
        Future.successful(req.copy(headers = req.headers + ("Authorization" -> s"Basic $encoded")))
      case tokenAuth: AccessTokenAuth =>
        Future.successful(req.copy(headers = req.headers + ("Authorization" -> s"Bearer ${tokenAuth.accessToken}")))
      case asyncAuth: AsyncAccessTokenAuth =>
        asyncAuth.accessToken().map(token =>
          req.copy(headers = req.headers + ("Authorization" -> s"Bearer $token"))
        )
      case null =>
        Future.successful(req)
    }
}
