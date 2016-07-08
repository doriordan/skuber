package skuber.api.security

import java.net.Socket
import javax.net.ssl._
import java.security.cert.X509Certificate
import java.security.SecureRandom

import skuber.api.client.{Context, PathOrData}

/**
 * @author David O'Riordan
 */
object TLS {
  
  // This trust manager supports the InsecureSkipTLSVerify flag in kubeconfig files -
  // it always trusts the server i.e. skips verifying the server cert for a TLS connection
  object InsecureSkipTLSVerifyTrustManager extends X509ExtendedTrustManager
  {
    def getAcceptedIssuers() = Array[X509Certificate]()
    def checkClientTrusted(certs: Array[X509Certificate], authType: String) : Unit = {}
    def checkServerTrusted(certs: Array[X509Certificate], authType: String) : Unit = {}
    def checkClientTrusted(certs: Array[X509Certificate], s: String, socket: Socket): Unit = {}
    def checkClientTrusted(certs: Array[X509Certificate], s: String, sslEngine: SSLEngine): Unit = {}
    def checkServerTrusted(certs: Array[X509Certificate], s: String, socket: Socket): Unit = {}
    def checkServerTrusted(certs: Array[X509Certificate], s: String, sslEngine: SSLEngine): Unit = {}
  }
   
  val skipTLSTrustManagers = Array[TrustManager](InsecureSkipTLSVerifyTrustManager)
 
  val HttpsPattern = "https:.*".r
  val HttpPattern = "http:.*".r
  
  def establishSSLContext(k8sContext: Context): Option[SSLContext] = {
    k8sContext.cluster.server match {
      case HttpPattern(_*) => None // not using SSL so return no context
      case HttpsPattern(_*) => Some(buildSSLContext(k8sContext))
      case _ => throw new Exception("Kubernetes cluster API server URL does not begin with either http or https : "
                                    + k8sContext.cluster.server)
    }
  }
  
   private def buildSSLContext(k8sContext: Context): SSLContext = {
     val sslContext = SSLContext.getInstance("TLS")
     
     val skipTLSVerify = k8sContext.cluster.insecureSkipTLSVerify
     val clusterCertConfig = k8sContext.cluster.certificateAuthority
     val trustManagers = getTrustManagers(skipTLSVerify,clusterCertConfig) 
          
     val clientCert = k8sContext.authInfo.clientCertificate
     val clientKey = k8sContext.authInfo.clientKey
     val user = k8sContext.authInfo.userName
     val keyManagers = getKeyManagers(user, clientCert, clientKey)
     
     sslContext.init(keyManagers.getOrElse(null), trustManagers.getOrElse(null),  new SecureRandom())
     sslContext
   }
   
   private def getTrustManagers(skipTLSVerify: Boolean, serverCertConfig: Option[PathOrData]) : Option[Array[TrustManager]] =
     if (skipTLSVerify) 
       Some(skipTLSTrustManagers)
     else
       serverCertConfig map { certPathOrData =>
           val clusterServerCert = SecurityHelper.getCertificate(certPathOrData)
           val trustStore = SecurityHelper.createTrustStore(clusterServerCert)
           val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
           tmf.init(trustStore)
           tmf.getTrustManagers
       }
  
   private def getKeyManagers(user: Option[String], clientCert: Option[PathOrData], clientKey: Option[PathOrData]) : Option[Array[KeyManager]] = 
     if (clientCert.isDefined && clientKey.isDefined) {
       val cert = SecurityHelper.getCertificate(clientCert.get)
       val key = SecurityHelper.getPrivateKey(clientKey.get)
       val keyStore = SecurityHelper.createKeyStore(user.getOrElse("skuber"), cert, key) 
       val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
       kmf.init(keyStore, "changeit".toCharArray)
       Some(kmf.getKeyManagers)
     }
     else
       None
       
}
