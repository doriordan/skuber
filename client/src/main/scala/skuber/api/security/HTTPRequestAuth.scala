package skuber.api.security

import akka.http.scaladsl.model.{HttpHeader, HttpRequest}
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, OAuth2BearerToken}
import skuber.api.client._

/**
 * @author David O'Riordan
 */
object HTTPRequestAuth {
  
  def addAuth(request: HttpRequest, auth: AuthInfo) : HttpRequest = {
    getAuthHeader(auth).map(request.addHeader).getOrElse(request)
  }

  def getAuthHeader(auth: AuthInfo) : Option[HttpHeader] = {
    auth match {
      case NoAuth | _: CertAuth => None
      case BasicAuth(user, password) => Some(Authorization(BasicHttpCredentials(user,password)))
      case auth: AccessTokenAuth => Some(Authorization(OAuth2BearerToken(auth.accessToken)))
    }
  }   
}
