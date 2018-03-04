package skuber.api.security

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, OAuth2BearerToken}
import skuber.api.client._

/**
 * @author David O'Riordan
 */
object HTTPRequestAuth {
  
  def addAuth(request: HttpRequest, auth: AuthInfo) : HttpRequest = {
    auth match {
      case NoAuth | _: CertAuth => request
      case BasicAuth(user, password) => request.addHeader(Authorization(BasicHttpCredentials(user,password)))
      case TokenAuth(token) => request.addHeader(Authorization(OAuth2BearerToken(token)))
      case OidcAuth(idToken) => request.addHeader(Authorization(OAuth2BearerToken(idToken)))
      case GcpAuth(accessToken) => request.addHeader(Authorization(OAuth2BearerToken(accessToken)))
    }
  }   
}
