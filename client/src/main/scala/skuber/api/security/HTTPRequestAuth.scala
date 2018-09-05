package skuber.api.security

import akka.http.scaladsl.model.{HttpHeader, HttpRequest}
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, OAuth2BearerToken}
import skuber.api.client._

/**
 * @author David O'Riordan
 */
object HTTPRequestAuth {
  
  def addAuth(request: HttpRequest, auth: AuthInfo) : HttpRequest = {
    getAuthHeaders(auth).foreach { header =>
      // Add headers one by one because `addHeaders()` doesn't convert the instance type
      request.addHeader(header)
    }
    request
  }

  def getAuthHeaders(auth: AuthInfo) : Seq[HttpHeader] = {
    auth match {
      case NoAuth | _: CertAuth => Seq()
      case BasicAuth(user, password) => Seq(Authorization(BasicHttpCredentials(user,password)))
      case auth: AccessTokenAuth => Seq(Authorization(OAuth2BearerToken(auth.accessToken)))
    }
  }   
}
