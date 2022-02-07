package skuber.api.security

import org.apache.commons.codec.binary.Base64

import java.io._
import java.nio.file.{Files, Paths}
import java.security.{KeyStore, PrivateKey}
import java.security.cert.{CertificateFactory, X509Certificate}
import java.security.KeyFactory
import java.security.InvalidKeyException
import java.security.spec.PKCS8EncodedKeySpec
import scala.io.Source
import scala.collection.JavaConverters._
import skuber.api.client.PathOrData
import skuber.api.security.SecurityHelper.PKCS8ECPrivateKeyParser.getPKCS8PrivateKey
import skuber.api.security.SecurityHelper.PrivateKeyParser

import java.nio.charset.StandardCharsets
import scala.util.{Success, Try}

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
   * Create a new Java keystore from client certificates and associated private key, with an optional password
   */
  def createKeyStore(user: String, clientCertificates: List[X509Certificate], clientPrivateKey: PrivateKey, password: Option[String] = None) : KeyStore = {
    val keyStore = KeyStore.getInstance("JKS")
    val keyStorePassword = password.orElse(Some("changeit")).get.toCharArray
    keyStore.load(null, keyStorePassword)
    keyStore.setKeyEntry(user, clientPrivateKey, keyStorePassword, clientCertificates.toArray)
    keyStore
  }

  def getPrivateKey(from: PathOrData) : PrivateKey = {
    val is = createInputStreamForPathOrData(from)
    readPrivateKey(is)
  }

  /*
   * Read a private key from an input stream containing PEM encoded PKCS#1 or PKCS#8
   * key data
   */
  private def readPrivateKey(is: InputStream): PrivateKey = {
    val pemData = Source.fromInputStream(is).mkString
    extractKeyFromPEMData(pemData).getOrElse(throw new Exception("Private key not in a supported PEM format"))
  }

  /**
    * This class encapsulates the PEM header/footer details for different key
    * formats, used to extract and parse the key using the correct algorithm,
    * @param header The expected PEM header for the key type
    * @param footer The expected PEM footer for the key type
    */
  case class PEMHeaderFooter(val header: String, val footer: String)

  val pkcs1PrivateKeyHdrFooter =
    PEMHeaderFooter("-----BEGIN RSA PRIVATE KEY-----","-----END RSA PRIVATE KEY-----")
  val pkcs8PrivateKeyHdrFooter =
    PEMHeaderFooter("-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----")
  // openssl may generate following header/footer specifically for EC case
  val pkcs8ECPrivateKeyHdrFooter =
    PEMHeaderFooter("-----BEGIN EC PRIVATE KEY-----", "-----END EC PRIVATE KEY-----")

  trait PrivateKeyParser
  {
    val headerFooter: PEMHeaderFooter

    def getPrivateKey(keyBytes: Array[Byte]): PrivateKey

    protected def getPKCS8PrivateKey(keyBytes: Array[Byte], algo: String): PrivateKey = {
      val spec = new PKCS8EncodedKeySpec(keyBytes)
      val factory = KeyFactory.getInstance(algo)
      factory.generatePrivate(spec)
    }
  }

  object PKCS1PrivateKeyParser extends PrivateKeyParser
  {
    override val headerFooter: PEMHeaderFooter = pkcs1PrivateKeyHdrFooter
    override def getPrivateKey(keyBytes: Array[Byte]): PrivateKey = {
      // java security API does not support pkcs#1 so convert to pkcs#8 RSA first
      val pkcs1Length = keyBytes.length;
      val totalLength = pkcs1Length + 22;
      val pkcs8Header: Array[Byte] = Array[Byte](
        0x30.toByte, 0x82.toByte, ((totalLength >> 8) & 0xff).toByte, (totalLength & 0xff).toByte, // Sequence + total length
        0x2.toByte, 0x1.toByte, 0x0.toByte, // Integer (0)
        0x30.toByte, 0xD.toByte, 0x6.toByte, 0x9.toByte, 0x2A.toByte, 0x86.toByte, 0x48.toByte, 0x86.toByte, 0xF7.toByte, 0xD.toByte, 0x1.toByte, 0x1.toByte, 0x1.toByte, 0x5.toByte, 0x0.toByte, // Sequence: 1.2.840.113549.1.1.1, NULL
        0x4.toByte, 0x82.toByte, ((pkcs1Length >> 8) & 0xff).toByte, (pkcs1Length & 0xff).toByte // Octet string + length
      )
      val pkcs8Bytes = pkcs8Header ++ keyBytes
      PKCS8PrivateKeyParser.getPrivateKey(pkcs8Bytes)
    }
  }

  object PKCS8PrivateKeyParser extends PrivateKeyParser
  {
    override val headerFooter: PEMHeaderFooter = pkcs8PrivateKeyHdrFooter
    override def getPrivateKey(keyBytes: Array[Byte]): PrivateKey = {
      // The PKCS#8 header doesn't specify the keys algo, so try RSA and EC in turn
      val algos = List("RSA", "EC")
      def tryWithAlgo(algo: String) = Try { getPKCS8PrivateKey(keyBytes,algo) }
      algos.map(tryWithAlgo)
        .collectFirst { case Success(result) => result }
        .getOrElse {
          throw new Exception(s"Failed trying to parse PCKS8 private key using each of $algos")
        }
    }
  }

  object PKCS8ECPrivateKeyParser extends PrivateKeyParser
  {
    override val headerFooter: PEMHeaderFooter = pkcs8ECPrivateKeyHdrFooter
    override def getPrivateKey(keyBytes: Array[Byte]): PrivateKey = getPKCS8PrivateKey(keyBytes, "EC")
  }

  val privateKeyParsers = List(PKCS1PrivateKeyParser,PKCS8PrivateKeyParser,PKCS8ECPrivateKeyParser)

  private def findPrivateKeyParser(pemData: String): Option[PrivateKeyParser] = {
    privateKeyParsers.find { parser =>
      pemData.startsWith(parser.headerFooter.header)
    }
  }

  private def extractKeyFromPEMData(pemData: String): Option[PrivateKey] = {
    val parserOpt = findPrivateKeyParser(pemData)
    parserOpt.map { parser =>
      val base64EncodedKey = pemData
          .replace(parser.headerFooter.header, "")
          .replace(parser.headerFooter.footer, "")
      val encoded = Base64.decodeBase64(base64EncodedKey)
      parser.getPrivateKey(encoded)
    }
  }
}
