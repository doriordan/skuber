package skuber.pekkoclient.impl

import org.apache.pekko.http.scaladsl.model.{HttpHeader, HttpRequest}
import org.apache.pekko.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, OAuth2BearerToken}
import skuber.api.client._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * @author David O'Riordan
  */
object HTTPRequestAuth {

  @deprecated("2.6.8", "Use addAuthAsync instead")
  def addAuth(request: HttpRequest, auth: AuthInfo) : HttpRequest = {
    getAuthHeader(auth).map(request.addHeader).getOrElse(request)
  }

  def addAuthAsync(request: HttpRequest, auth: AuthInfo): Future[HttpRequest] = {
    getAuthHeaderAsync(auth).map(_.map(request.addHeader).getOrElse(request))(ExecutionContext.parasitic)
  }

  @deprecated("2.6.8", "Use addAuthAsync instead")
  def getAuthHeader(auth: AuthInfo) : Option[HttpHeader] = {
    auth match {
      case NoAuth | _: CertAuth => None
      case BasicAuth(user, password) => Some(Authorization(BasicHttpCredentials(user,password)))
      case auth: AccessTokenAuth => Some(Authorization(OAuth2BearerToken(auth.accessToken)))
      case _: AsyncAccessTokenAuth => sys.error("Async auth does not work with getAuthHeader")
    }
  }

  def getAuthHeaderAsync(auth: AuthInfo): Future[Option[HttpHeader]] = {
    auth match {
      case NoAuth | _: CertAuth => Future.successful(None)
      case BasicAuth(user, password) => Future.successful(Some(Authorization(BasicHttpCredentials(user, password))))
      case auth: AccessTokenAuth => Future.successful(Some(Authorization(OAuth2BearerToken(auth.accessToken))))
      case asyncAuth: AsyncAccessTokenAuth => asyncAuth.accessToken().map(token => Some(Authorization(OAuth2BearerToken(token))))(ExecutionContext.parasitic)
    }
  }
}
