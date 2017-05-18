package skuber.api.security

import scala.io.Source
import java.io.{InputStream, ByteArrayInputStream, FileInputStream}
import java.nio.file.{Path,Paths,Files}
import java.util.Base64
import java.security.{KeyStore, KeyFactory, PrivateKey }
import java.security.cert.{Certificate, X509Certificate, CertificateFactory}
import java.security.spec.{PKCS8EncodedKeySpec, RSAPrivateKeySpec}
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
  
  def readPrivateKey(is: InputStream, keyAlgo: String = "RSA"): PrivateKey = {   
    val keyPemLines = Source.fromInputStream(is).getLines.toList
    val isPKCS1 = keyPemLines.headOption.map(_.contains(" RSA ")).getOrElse(false)
    val keyLines = keyPemLines.filterNot (l => l.contains("-----BEGIN") || l.contains("-----END"))
    val keyBase64Encoded = keyLines.mkString // concat the key lines to give a single base64 encoded key
    val keyBytes: Array[Byte] = Base64.getDecoder.decode(keyBase64Encoded) 
    val keySpec = 
      if (isPKCS1)
        SecurityHelper.PCKS1Helper.decodeKey(keyBytes)
      else {  
        // if not PKCS#1 then assume it is PKCS#8
        val keFactory = KeyFactory.getInstance(keyAlgo)   
        new PKCS8EncodedKeySpec(keyBytes)
      }
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePrivate(keySpec)
  }
  
  def getPrivateKey(from: PathOrData, keyAlgo: String = "RSA") : PrivateKey = {
    val is = createInputStreamForPathOrData(from)
    readPrivateKey(is, keyAlgo)
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
  
  private object PCKS1Helper {

    // This helper supports cases where private key is PKCS1 encoded, as unlike PKCS8 we don't seem to get standard Java
    // library support for parsing keys encoded in that format
    
    // The code below is partly inspired by - and therefore credit owed to authors of - source of net.auth.core Java lib
    
    import java.io.IOException
    import java.math.BigInteger;
    import java.security.spec.RSAPrivateCrtKeySpec;
    
    case class ASN1Value(id: Int, contents: Array[Byte]) {
      
      def asBigInteger: BigInteger = {
        if ((id & 0x1f) != 0x02)
          throw new IOException("Invalid DER format for private key: expected INTEGER")
        new BigInteger(contents)
      }
      
    }
    
    class DERParser(input: ByteArrayInputStream) {
      
      def readASN1: ASN1Value = {
        val id = input.read
        val lenInitialByte = input.read
        val len = if (lenInitialByte <= 0x7f)
                     lenInitialByte 
                  else {
                    // length is encoded over several octets
                    val numBytesInLenEncoding = lenInitialByte & 0x7f
                    if (numBytesInLenEncoding < 1 || numBytesInLenEncoding > 4)
                      throw new IOException("Invalid DER format for private key: bad length encoding")
                    val lenEncodingBytes= new Array[Byte](numBytesInLenEncoding)
                    val numRead = input.read(lenEncodingBytes,0,numBytesInLenEncoding)
                    if (numRead < numBytesInLenEncoding)
                      throw new IOException("Invalid DER format for private key: not enough bytes for length encoding")
                    new BigInteger(1, lenEncodingBytes).intValue
                  } 
        val content = new Array[Byte](len)
        val numRead = input.read(content,0,len)
        if (numRead < len)
            throw new IOException("Invalid DER format for private key: not enough bytes for content encoding")
        ASN1Value(id,content)
      }
    }
    def decodeKey(pkcs1Bytes: Array[Byte]) : RSAPrivateKeySpec = {
      val input=new ByteArrayInputStream(pkcs1Bytes)
      val seqParser = new DERParser(input)
      val sequence = seqParser.readASN1
      if ((sequence.id & 0x3f) != 0x30)      
        throw new IOException("Invalid DER format for private key: expected SEQUENCE, got: " + sequence.id)
      
      val encapsulatedInput=new ByteArrayInputStream(sequence.contents)
      val parser = new DERParser(encapsulatedInput)
      parser.readASN1
      
      val modulus=parser.readASN1.asBigInteger
      val publicExponent = parser.readASN1.asBigInteger
      val privateExponent = parser.readASN1.asBigInteger
      val primeP = parser.readASN1.asBigInteger
      val primeQ = parser.readASN1.asBigInteger
      val primeExponentP = parser.readASN1.asBigInteger
      val primeExponentQ = parser.readASN1.asBigInteger
      val crtCoefficient = parser.readASN1.asBigInteger
      
      new RSAPrivateCrtKeySpec(
        modulus,
        publicExponent,
        privateExponent,
        primeP,
        primeQ,
        primeExponentP,
        primeExponentQ,
        crtCoefficient)
    }
  }
}