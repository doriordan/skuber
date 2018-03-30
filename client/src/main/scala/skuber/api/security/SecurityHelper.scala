package skuber.api.security

import java.io._
import java.nio.file.{Files, Paths}
import java.security.{KeyStore, PrivateKey}
import java.security.cert.{CertificateFactory, X509Certificate}

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}

import scala.collection.JavaConverters._
import skuber.api.client.PathOrData

/**
 * @author David O'Riordan
 */
object SecurityHelper {
    
  /*
   * Read all X509 client certificates from a given input stream
   */
  def readCertificates(is: InputStream) : List[X509Certificate] =
    CertificateFactory.getInstance("X509").generateCertificates(is).asScala.collect {
      case cert: X509Certificate => cert
      case _ => throw new Exception("Not X509 cert?")
    }.toList

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
   * Get certificates given the file path or embedded data for it
   */
  def getCertificates(from: PathOrData): List[X509Certificate] = {
    val is = createInputStreamForPathOrData(from)
    readCertificates(is)
  }
  
  /*
   * Create a new trust store that trusts the specified Kubernetes API server certificates
   */
  def createTrustStore(apiServerCerts: List[X509Certificate]) : KeyStore = {
    val trustStore = KeyStore.getInstance("JKS")
    trustStore.load(null) // create an empty trust store
    apiServerCerts.foldLeft(trustStore){ (trustStore, apiServerCert) =>
      val alias = apiServerCert.getSubjectX500Principal.getName
      trustStore.setCertificateEntry(alias, apiServerCert)
      trustStore
    }
  }
  
  /*
   * Read a private key from an input stream according to specified algorithm (defaults to RSA)
   * Note: Currently the stream must contain a PEM format file with a PKCS#8 encoded key
   */
  
  def readPrivateKey(is: InputStream): PrivateKey = {
    val provider = new BouncyCastleProvider()
    val converter = new JcaPEMKeyConverter().setProvider(provider);
    Option(new PEMParser(new InputStreamReader(is)).readObject()) match {
      case Some(obj) => obj match {
        case pair: PEMKeyPair => converter.getPrivateKey(pair.getPrivateKeyInfo)
        case pk: PrivateKeyInfo => converter.getPrivateKey(pk)
      }
      case None => throw new IOException("could not read private key")
    }
  }
  
  def getPrivateKey(from: PathOrData) : PrivateKey = {
    val is = createInputStreamForPathOrData(from)
    readPrivateKey(is)
  }
  
  /*
   * Create a new Java keystore from client certificates and associated private key, with an optional password
   */
  def createKeyStore(user: String, clientCertificates: List[X509Certificate], clientPrivateKey: PrivateKey, password: Option[String] = None) : KeyStore = {
    val keyStore = KeyStore.getInstance("JKS")
    val keyStorePassword = password.orElse(Some("changeit")).get.toCharArray
    keyStore.load(null, keyStorePassword)
    keyStore.setKeyEntry(user, clientPrivateKey, keyStorePassword, clientCertificates.toArray)
    keyStore
  }

}