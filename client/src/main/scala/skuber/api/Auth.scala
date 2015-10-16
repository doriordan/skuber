package skuber.api

import client._
import javax.net.ssl.SSLContext
import javax.net.ssl.{TrustManager,X509TrustManager, X509ExtendedTrustManager};
import java.security.cert.X509Certificate;
import play.api.libs.ws.{WSRequest,WSAuthScheme}

/**
 * @author David O'Riordan
 */
package object Auth {
  sealed trait ClientAuth
  case object NoAuth extends ClientAuth
  case class Token(token: String) extends ClientAuth
  // Future support: case class ClientCertificateAuth(certificate: String) extends ClientAuth 
  case class BasicAuth(userName: String, password: String) extends ClientAuth
  
  val skipTLSTrustManagers = Array[TrustManager](new InsecureSkipTLSVerifyTrustManager)
  val defaultTrustManagers = Array[TrustManager]()
    
  val HttpsPattern = "https:.*".r
  val HttpPattern = "http:.*".r
  
  def establishSSLContext(k8sContext: K8SContext): Option[SSLContext] = {
    k8sContext.cluster.server match {
      case HttpPattern(_*) => None // not using SSL so return no context
      case HttpsPattern(_*) => Some(buildSSLContext(k8sContext))
      case _ => throw new Exception("Kubernetes cluster API server URL does not begin with either http or https : "
                                    + k8sContext.cluster.server)
    }
  }
  
  private def buildSSLContext(k8sContext: K8SContext): SSLContext = {
    val sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, 
                    if (k8sContext.cluster.insecureSkipTLSVerify)
                       // no check of server certificate
                      skipTLSTrustManagers
                    else
                       // verify server cert NB it needs to be in the trust store
                      defaultTrustManagers,
                    null)
    sslContext 
  }
  
  def establishClientAuth(k8sContext: K8SContext) : ClientAuth = {
    // supports bearer token, basic auth or no auth options at the moment -
    // any client certificate info in the context is ignored
    val maybeToken=k8sContext.authInfo.token
    val maybeUserName=k8sContext.authInfo.userName
    val maybePassword=k8sContext.authInfo.password
    (maybeToken, maybeUserName, maybePassword) match {
      case (None, None, None) => NoAuth 
      case (Some(token), None, None) => Token(token)
      case (None, Some(userName), Some(password)) => BasicAuth(userName, password)
      case (None, Some(userName), None) => throw new Exception("No password provided for user " + userName)
      case (None, None, Some(password)) => throw new Exception("Password provided but no username found for user")
      case _ => throw new Exception("Only one of token or username/password may be specfied")
    }
  }
  
  def addAuth(request: WSRequest, auth: ClientAuth) : WSRequest = {
      auth match {
        case NoAuth => request
        case BasicAuth(user, password) => request.withAuth(user,password,WSAuthScheme.BASIC)
        case Token(token) => request.withHeaders("Authorization" -> ("BEARER " + token))
      }
  }   
  
  // This class is for supporting the InsecureSkipTLSVerify flag in kubeconfig files -
  // it always trusts the server i.e. skips verifying the server cert. Not recommended
  // for production usage!
  class InsecureSkipTLSVerifyTrustManager extends X509TrustManager
  {
    def getAcceptedIssuers() = Array[X509Certificate]()
    def checkClientTrusted(certs: Array[X509Certificate], authType: String) : Unit = {}
    def checkServerTrusted(certs: Array[X509Certificate], authType: String) : Unit = {}
  }
  
  
}
