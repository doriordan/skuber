package skuber.api.security

import scala.io.Source
import java.io.{InputStream, ByteArrayInputStream, FileInputStream}
import java.nio.file.{Path,Paths,Files}
import java.util.Base64
import java.security.{KeyStore, KeyFactory, PrivateKey }
import java.security.cert.{Certificate, X509Certificate, CertificateFactory}
import java.security.spec.PKCS8EncodedKeySpec;

import skuber.api.client.PathOrData

/**
 * @author David O'Riordan
 */
object SecurityHelper {
    
  /*
   * Read an X509 client certificate from a given input stream
   */
  def readCertificate(is: InputStream) : X509Certificate = 
    CertificateFactory.getInstance("X509").generateCertificate(is).asInstanceOf[X509Certificate]
    
 
  private def createInputStreamForPathOrData(target: PathOrData) : InputStream = {
    target match {
      case Right(data) => new ByteArrayInputStream(data)
      case Left(path)  => {
        val filePath = Paths.get(path)
        Files.newInputStream(filePath)
      }
    }
  }
  
  /*
   * Get a certificate given the file path or embedded data for it  
   */
  def getCertificate(from: PathOrData) = {
    val is = createInputStreamForPathOrData(from)
    readCertificate(is)
  }
  
  /*
   * Create a new trust store that trusts the specified Kubernetes API server certificate 
   */
  def createTrustStore(apiServerCert: X509Certificate) : KeyStore = {
    val trustStore = KeyStore.getInstance("JKS")
    trustStore.load(null) // create an empty trust store
    val alias = apiServerCert.getSubjectX500Principal.getName
    trustStore.setCertificateEntry(alias, apiServerCert) 
    trustStore
  }
  
  /*
   * Read a private key from an input stream according to specified algorithm (defaults to RSA)
   * Note: Currently the stream must contain a PEM format file with a PKCS#8 encoded key
   */
  
  def readPrivateKey(is: InputStream, keyAlgo: String = "RSA"): PrivateKey = {   
    val keyPemLines = Source.fromInputStream(is).getLines
    val keyLines = keyPemLines.filterNot (l => l.contains("-----BEGIN") || l.contains("-----END"))
    val keyBase64Encoded = keyLines.mkString // concat the key lines to give a single base64 encoded key
    val keyBytes: Array[Byte] = Base64.getDecoder.decode(keyBase64Encoded) 
    
    val keyFactory = KeyFactory.getInstance(keyAlgo)   
    val kspec = new PKCS8EncodedKeySpec(keyBytes)
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePrivate(kspec)
  }
  
  def getPrivateKey(from: PathOrData, keyAlgo: String = "RSA") : PrivateKey = {
    val is = createInputStreamForPathOrData(from)
    readPrivateKey(is, keyAlgo)
  }
  
  /*
   * Create a new Java keystore from a client certificate and associated private key, with an optional password
   */
  def createKeyStore(user: String, clientCertificate: X509Certificate, clientPrivateKey: PrivateKey, password: Option[String] = None) : KeyStore = {
    val keyStore = KeyStore.getInstance("JKS")
    val keyStorePassword = password.orElse(Some("changeit")).get.toCharArray
    keyStore.load(null, keyStorePassword)
    keyStore.setKeyEntry(user, clientPrivateKey, keyStorePassword, Array(clientCertificate))   
    keyStore
  } 
}