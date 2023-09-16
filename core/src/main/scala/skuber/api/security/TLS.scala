package skuber.api.security

import java.net.Socket
import javax.net.ssl._
import java.security.cert.X509Certificate
import java.security.SecureRandom

import skuber.api.client.{AuthInfo, CertAuth, Context, PathOrData}

/**
 * @author David O'Riordan
 */
object TLS {
  
  // This trust manager supports the InsecureSkipTLSVerify flag in kubeconfig files -
  // it always trusts the server i.e. skips verifying the server cert for a TLS connection
  object InsecureSkipTLSVerifyTrustManager extends X509ExtendedTrustManager
  {
    def getAcceptedIssuers = Array.empty[X509Certificate]
    def checkClientTrusted(certs: Array[X509Certificate], authType: String) : Unit = {}
    def checkServerTrusted(certs: Array[X509Certificate], authType: String) : Unit = {}
    def checkClientTrusted(certs: Array[X509Certificate], s: String, socket: Socket): Unit = {}
    def checkClientTrusted(certs: Array[X509Certificate], s: String, sslEngine: SSLEngine): Unit = {}
    def checkServerTrusted(certs: Array[X509Certificate], s: String, socket: Socket): Unit = {}
    def checkServerTrusted(certs: Array[X509Certificate], s: String, sslEngine: SSLEngine): Unit = {}
  }
   
  private val skipTLSTrustManagers: Array[TrustManager] = Array[TrustManager](InsecureSkipTLSVerifyTrustManager)
 
  private val HttpsPattern = "https:.*".r
  private val HttpPattern = "http:.*".r
  
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

     val keyManagers = getKeyManagers(k8sContext.authInfo)
     
     sslContext.init(keyManagers.orNull, trustManagers.orNull, new SecureRandom())
     sslContext
   }
   
   private def getTrustManagers(skipTLSVerify: Boolean, serverCertConfig: Option[PathOrData]) : Option[Array[TrustManager]] =
     if (skipTLSVerify) 
       Some(skipTLSTrustManagers)
     else
       serverCertConfig map { certPathOrData =>
           val clusterServerCerts = SecurityHelper.getCertificates(certPathOrData)
           val trustStore = SecurityHelper.createTrustStore(clusterServerCerts)
           val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
           tmf.init(trustStore)
           tmf.getTrustManagers
       }
  
   private def getKeyManagers(authInfo: AuthInfo) : Option[Array[KeyManager]] =
     authInfo match {
       case CertAuth(clientCert, clientKey, userName) =>
         val certs = SecurityHelper.getCertificates(clientCert)
         val key = SecurityHelper.getPrivateKey(clientKey)
         val keyStore = SecurityHelper.createKeyStore(userName.getOrElse("skuber"), certs, key)
         val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
         kmf.init(keyStore, "changeit".toCharArray)
         Some(kmf.getKeyManagers)
       case _ => None
     }
       
}
