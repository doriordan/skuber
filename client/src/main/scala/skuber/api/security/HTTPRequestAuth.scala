package skuber.api.security

import javax.net.ssl.SSLContext
import javax.net.ssl.{TrustManager,X509TrustManager, X509ExtendedTrustManager};
import java.security.cert.X509Certificate;
import play.api.libs.ws.{WSRequest,WSAuthScheme}

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
    val maybeUserName=k8sContext.authInfo.userName
    val maybePassword=k8sContext.authInfo.password
    (maybeToken, maybeUserName, maybePassword) match {
      case (None, None, None) => NoAuth 
      case (Some(token), None, None) => Token(token)
      case (None, Some(userName), Some(password)) => BasicAuth(userName, password)
      case (None, Some(userName), None) => throw new Exception("No password provided for user " + userName)
      case (None, None, Some(password)) => throw new Exception("Password provided but no username found for user")
      case _ => throw new Exception("Only one of token or username/password may be specified")
    }
  }
  
  def addAuth(request: WSRequest, auth: RequestAuth) : WSRequest = {
      auth match {
        case NoAuth => request
        case BasicAuth(user, password) => request.withAuth(user,password,WSAuthScheme.BASIC)
        case Token(token) => request.withHeaders("Authorization" -> ("BEARER " + token))       
      }
  }   
}
