package skuber.api.security

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, OAuth2BearerToken}
import skuber.api.client.Context

/**
 * @author David O'Riordan
 */
object HTTPRequestAuth {
  sealed trait RequestAuth
  case object NoAuth extends RequestAuth
  case class Token(token: String) extends RequestAuth
  case class BasicAuth(userName: String, password: String) extends RequestAuth  
  
  def establishRequestAuth(k8sContext: Context) : RequestAuth = {
    val maybeToken=k8sContext.authInfo.token
    val maybeJWT=k8sContext.authInfo.jwt
    val maybeUserName=k8sContext.authInfo.userName
    val maybePassword=k8sContext.authInfo.password
    (maybeToken, maybeUserName, maybePassword,maybeJWT) match {
      case (None, None, None, None) => NoAuth
      case (Some(token), None, None, None) => Token(token)
      case (None, None, None,Some(token)) => Token(token)
      case (None, Some(userName), Some(password), None) => BasicAuth(userName, password)
      case (None, Some(userName), None, None) => throw new Exception("No password provided for user " + userName)
      case (None, None, Some(password), None) => throw new Exception("Password provided but no username found for user")
      case _ => throw new Exception("Only one of token or username/password may be specified")
    }
  }
  
  def addAuth(request: HttpRequest, auth: RequestAuth) : HttpRequest = {
      auth match {
        case NoAuth => request
        case BasicAuth(user, password) => request.addHeader(Authorization(BasicHttpCredentials(user,password)))
        case Token(token) => request.addHeader(Authorization(OAuth2BearerToken(token)))
      }
  }   
}
