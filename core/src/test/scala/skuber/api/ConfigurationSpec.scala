package skuber.api

import java.nio.file.Paths
import java.time.Instant
import org.specs2.mutable.Specification

import skuber.api.client._
import skuber.model.Namespace

import java.io.ByteArrayInputStream
import java.security.Signature
import skuber.api.security.SecurityHelper

/**
 * @author David O'Riordan
 */
class ConfigurationSpec extends Specification {

  implicit val loggingContext: LoggingContext = new LoggingContext { override def output:String="test" }

  val kubeConfigStr =  """
apiVersion: v1
clusters:
- cluster:
    api-version: v1
    server: http://cow.org:8080
  name: cow-cluster
- cluster:
    certificate-authority: path/to/my/cafile
    server: https://horse.org:4443
  name: horse-cluster
- cluster:
    insecure-skip-tls-verify: true
    server: https://pig.org:443
  name: pig-cluster
contexts:
- context:
    cluster: horse-cluster
    namespace: chisel-ns
    user: green-user
  name: federal-context
- context:
    cluster: pig-cluster
    namespace: saw-ns
    user: blue-user
  name: queen-anne-context
current-context: federal-context
kind: Config
preferences:
  colors: true
users:
- name: blue-user
  user:
    token: blue-token
- name: green-user
  user:
    client-certificate: path/to/my/client/cert
    client-key: path/to/my/client/key
- name: jwt-user
  user:
    auth-provider:
      config:
        client-id: tectonic
        client-secret: secret
        extra-scopes: groups
        id-token: jwt-token
        idp-certificate-authority-data: data
        idp-issuer-url: https://xyz/identity
        refresh-token: refresh
      name: oidc
- name: gke-user
  user:
    auth-provider:
      config:
        access-token: myAccessToken
        cmd-args: config config-helper --format=json
        cmd-path: /home/user/google-cloud-sdk/bin/gcloud
        expiry: 2018-03-04T14:08:18Z
        expiry-key: '{.credential.token_expiry}'
        token-key: '{.credential.access_token}'
      name: gcp
- name: string-date-gke-user
  user:
    auth-provider:
      config:
        access-token: myAccessToken
        cmd-args: config config-helper --format=json
        cmd-path: /home/user/google-cloud-sdk/bin/gcloud
        expiry: "2018-03-04T14:08:18Z"
        expiry-key: '{.credential.token_expiry}'
        token-key: '{.credential.access_token}'
      name: gcp
- name: other-date-gke-user
  user:
    auth-provider:
      config:
        cmd-args: config config-helper --format=json
        cmd-path: /home/user/google-cloud-sdk/bin/gcloud
        expiry: "2018-03-04 14:08:18"
        expiry-key: '{.credential.token_expiry}'
        token-key: '{.credential.access_token}'
      name: gcp
"""

  // Test key material: each client certificate matches the private keys paired with it below. The EC and RSA
  // keys are provided in the formats that Kubernetes tooling is known to generate.
  private val caCertPem =
    """-----BEGIN CERTIFICATE-----
MIIBgDCCASegAwIBAgIUUaq2BM0XZk0UoM58CFExvhI4MjwwCgYIKoZIzj0EAwIw
HTEbMBkGA1UEAxMSVUNQIENsaWVudCBSb290IENBMB4XDTE4MDMxOTE1NTAwMFoX
DTIzMDMxODE1NTAwMFowHTEbMBkGA1UEAxMSVUNQIENsaWVudCBSb290IENBMFkw
EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEkzMcbk4TMsyUqg2bIJ/NtshBzlVp7+zt
gKkTwGlgXoOkgywrCAiMXZy8HoztQ65rwt5umB5KLW/xRR/o4LOrSqNFMEMwDgYD
VR0PAQH/BAQDAgEGMBIGA1UdEwEB/wQIMAYBAf8CAQIwHQYDVR0OBBYEFJsh4q9o
JYWOo0k1tbjBZCnC5xWoMAoGCCqGSM49BAMCA0cAMEQCIDe2jpGJmZSS/KHHlfJq
vu9ausd+96NkGH5HarXCtk4mAiBJyTIF2fNZglsdG7Q+4hnSgmDxH3YGt+DcW2bg
bcEepQ==
-----END CERTIFICATE-----
-----BEGIN CERTIFICATE-----
MIIBajCCARCgAwIBAgIUZZM2OPT0m41DfCs1LFnpbsa/xYowCgYIKoZIzj0EAwIw
EzERMA8GA1UEAxMIc3dhcm0tY2EwHhcNMTgwMzE5MTU1MDAwWhcNMzgwMzE0MTU1
MDAwWjATMREwDwYDVQQDEwhzd2FybS1jYTBZMBMGByqGSM49AgEGCCqGSM49AwEH
A0IABNsUJ5bxoEfn5UWKmSCvhssep7nnJOkWKPUKix3Jxoo9M4zuYEBvJEWEZrbg
brUcO0vr3pVzlARo7MrYnMLKOSmjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNVHRMB
Af8EBTADAQH/MB0GA1UdDgQWBBSthOLuLIsW/jO8w0J2X3xC3AUcQDAKBggqhkjO
PQQDAgNIADBFAiEA940pbqDBzhj+MsH2XCQdiRrUBAfO5dWF+uaZPIg8pG8CIHQy
SQB8EKl3rfOVzR9/fSqH6odaVPBMF+yjZNUbxEDH
-----END CERTIFICATE-----"""

  private val ecClientCertPem =
    """-----BEGIN CERTIFICATE-----
MIICBDCCAaugAwIBAgIUJ0nYl/SU2qdfOdT1IFQn+/la9NowCgYIKoZIzj0EAwIw
EzERMA8GA1UEAxMIc3dhcm0tY2EwHhcNMTgwMzE5MTcxMDAwWhcNMjgwMzE2MTcx
MDAwWjBsMQkwBwYDVQQGEwAxCTAHBgNVBAgTADEJMAcGA1UEBxMAMSgwJgYDVQQK
Ex9PcmNhOiB2b2NtNHFid2ZvMWtsajVvbHpvNTNubGtvMQ8wDQYDVQQLEwZDbGll
bnQxDjAMBgNVBAMTBWFkbWluMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEC+Td
2vrslR6vJIC9NH3Ml6TWGrIU9NHls12jJ2mmJN6yCyct05yojhc17RcZTsU6UA7r
nyOZdBhpwJeDiTLI9KOBgzCBgDAOBgNVHQ8BAf8EBAMCBaAwEwYDVR0lBAwwCgYI
KwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQUYnGweZWV6x2IvbQDZ/HQ
oKWizFswHwYDVR0jBBgwFoAUrYTi7iyLFv4zvMNCdl98QtwFHEAwCwYDVR0RBAQw
AoEAMAoGCCqGSM49BAMCA0cAMEQCIBiR2p9EBQP75LElMKWpJeA77hVRO05WeY7t
Cpc3G00BAiB4Rnhv2odU1YpucnZ23fXaGMsvi/AiXrzEb8Q83iRxSg==
-----END CERTIFICATE-----"""

  private val ecSec1KeyPem =
    """-----BEGIN EC PRIVATE KEY-----
MHcCAQEEIJOT1I+ztIW8lHJaUddPgHIcMMX+mZn336tm9wEEXFzSoAoGCCqGSM49
AwEHoUQDQgAEC+Td2vrslR6vJIC9NH3Ml6TWGrIU9NHls12jJ2mmJN6yCyct05yo
jhc17RcZTsU6UA7rnyOZdBhpwJeDiTLI9A==
-----END EC PRIVATE KEY-----"""

  private val ecPkcs8KeyPem =
    """-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgk5PUj7O0hbyUclpR
10+Achwwxf6Zmfffq2b3AQRcXNKhRANCAAQL5N3a+uyVHq8kgL00fcyXpNYashT0
0eWzXaMnaaYk3rILJy3TnKiOFzXtFxlOxTpQDuufI5l0GGnAl4OJMsj0
-----END PRIVATE KEY-----"""

  private val rsaClientCertPem =
    """-----BEGIN CERTIFICATE-----
MIICSDCCATCgAwIBAgIIFRpXSUCEdI8wDQYJKoZIhvcNAQELBQAwFTETMBEGA1UE
AxMKa3ViZXJuZXRlczAeFw0xODAzMDkxOTI5NTZaFw0xOTAzMDkxOTI5NTZaMDMx
FDASBgNVBAoTC0RvY2tlciBJbmMuMRswGQYDVQQDExJkb2NrZXItZm9yLWRlc2t0
b3AwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBANFEFtJOuKKN9VkQ+2yWFzwO
ABOghQ3yiHLARJP8pqXtCCuTWNpxwbRs9N9PrxSn0BnVsyykxiQ6MvpzK9kCylAN
/X6Oo1jYx/+PXtzu401supnHsIr9KUMAxGTGN+CbyyQ/vpL9MJuDWUKQMGaKc6AS
NNvA0UEMXCPI4+0stfQBAgMBAAGjAjAAMA0GCSqGSIb3DQEBCwUAA4IBAQA8Wm6N
ui5p/TFPTDtls4ivowqiam4v1S9i5m2+RApBEFkjZWzM1T8QggTsQlt66phXGHvW
azpJc7wj437O6iDgCE0uqbbd7ldO6MooVzk3ShNkSf+QSPwwQw9ABTMGMIp/jc4E
Y5KF5tnbA9zotSYJbwRZTmIAESIT2tXJiirPPKMr7zHSVCieRV3Rf1e04PBorg9J
/5hdes44TXGbI+w9DjhuvdhQ7HvDGlv6z2jlK/aasqAshjQmUoGwDDzPllgdRnZv
vqgvZz/IVMqV9xA3ofC9L1PauzAFtLc4uYLXEkRlGgEqP67dcmYqdRVAp8ZEA/5j
M9iqMvMu6sysxSAa
-----END CERTIFICATE-----"""

  private val rsaPkcs1KeyPem =
    """-----BEGIN RSA PRIVATE KEY-----
MIICXAIBAAKBgQDRRBbSTriijfVZEPtslhc8DgAToIUN8ohywEST/Kal7Qgrk1ja
ccG0bPTfT68Up9AZ1bMspMYkOjL6cyvZAspQDf1+jqNY2Mf/j17c7uNNbLqZx7CK
/SlDAMRkxjfgm8skP76S/TCbg1lCkDBminOgEjTbwNFBDFwjyOPtLLX0AQIDAQAB
AoGASWZThuKbulCGjP364iRm8+aJOlkkMjcuiulLZIjKvwo7wmUFVmFuKuXIogcm
Bt2xjU46cV8+LHjJZrU83PowkW8t2qQ9hWafGemV4mhnb1DacgFSO26ls+EOCs84
L2N4tzRzfMQXdwupnzSTB64lWXCn3wY/dVm2AH9BSv5V6p0CQQDbfLJXueiMEMW8
mpNs1RAz162hfYLGUNbtXBJNqro1Q4mQ/5zyBmj/PagI4NYhwjlfIfAmEPqnnjuG
ij3IJWoLAkEA9BQa8GGTFtd+gcs5s6kNz1imgLGakDdVq+Gf+sjKoz75XX7sKKyy
LMnW18g9JhhR/wuS2sVQMSQ0+izvWu6towJBAJ43Uy/duX5OENwV6TPImrdkP6tr
mDtwxp2vgxotebEvkBjPycjFSibDwT81I+aN5WFoS36FOspdS7d+K279WWUCQGdi
3MZVjmhufzebTaW8Rg0+D8kTcdUEm1VjDNC9nJfpZNclnALeo9o05LwiJUStqI3P
6TShv4YRQB94SSrLTuECQAtJ9V/UH7Y9xq9dwrsVFS3aENQCN+lA8VB0cqfNH9iR
NDYYE7FnRPWn8ViMvwlOss6eTN8HXdkmz6c//WCrpCM=
-----END RSA PRIVATE KEY-----"""

  private val rsaPkcs8KeyPem =
    """-----BEGIN PRIVATE KEY-----
MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBANFEFtJOuKKN9VkQ
+2yWFzwOABOghQ3yiHLARJP8pqXtCCuTWNpxwbRs9N9PrxSn0BnVsyykxiQ6Mvpz
K9kCylAN/X6Oo1jYx/+PXtzu401supnHsIr9KUMAxGTGN+CbyyQ/vpL9MJuDWUKQ
MGaKc6ASNNvA0UEMXCPI4+0stfQBAgMBAAECgYBJZlOG4pu6UIaM/friJGbz5ok6
WSQyNy6K6UtkiMq/CjvCZQVWYW4q5ciiByYG3bGNTjpxXz4seMlmtTzc+jCRby3a
pD2FZp8Z6ZXiaGdvUNpyAVI7bqWz4Q4KzzgvY3i3NHN8xBd3C6mfNJMHriVZcKff
Bj91WbYAf0FK/lXqnQJBANt8sle56IwQxbyak2zVEDPXraF9gsZQ1u1cEk2qujVD
iZD/nPIGaP89qAjg1iHCOV8h8CYQ+qeeO4aKPcglagsCQQD0FBrwYZMW136Byzmz
qQ3PWKaAsZqQN1Wr4Z/6yMqjPvldfuworLIsydbXyD0mGFH/C5LaxVAxJDT6LO9a
7q2jAkEAnjdTL925fk4Q3BXpM8iat2Q/q2uYO3DGna+DGi15sS+QGM/JyMVKJsPB
PzUj5o3lYWhLfoU6yl1Lt34rbv1ZZQJAZ2LcxlWOaG5/N5tNpbxGDT4PyRNx1QSb
VWMM0L2cl+lk1yWcAt6j2jTkvCIlRK2ojc/pNKG/hhFAH3hJKstO4QJAC0n1X9Qf
tj3Gr13CuxUVLdoQ1AI36UDxUHRyp80f2JE0NhgTsWdE9afxWIy/CU6yzp5M3wdd
2SbPpz/9YKukIw==
-----END PRIVATE KEY-----"""

  /** Emits a kubeconfig containing the given PEM data, base64 encoded as per the `*-data` kubeconfig fields */
  private def kubeconfigWith(caPem: String, clientCertPem: String, clientKeyPem: String): String = {
    def b64(pem: String) = java.util.Base64.getEncoder.encodeToString((pem.trim + "\n").getBytes("UTF-8"))
    s"""
apiVersion: v1
kind: Config
clusters:
- name: test-cluster
  cluster:
    certificate-authority-data: ${b64(caPem)}
    server: https://horse.org:4443
contexts:
- name: test-context
  context:
    cluster: test-cluster
    user: test-user
current-context: test-context
users:
- name: test-user
  user:
    client-certificate-data: ${b64(clientCertPem)}
    client-key-data: ${b64(clientKeyPem)}
"""
  }

  /**
   * Parses a kubeconfig built from the given PEM data, and verifies that the CA certificate, client certificate
   * and private key are all decoded, and that the private key matches the client certificate.
   */
  private def parseAndVerifyKeys(clientCertPem: String, clientKeyPem: String, expectedKeyAlgorithm: String, sigAlgorithm: String) = {
    val is = new ByteArrayInputStream(kubeconfigWith(caCertPem, clientCertPem, clientKeyPem).getBytes("UTF-8"))
    val k8sConfigTry = K8SConfiguration.parseKubeconfigStream(is)
    k8sConfigTry must beSuccessfulTry

    val config = k8sConfigTry.get
    val caCerts = SecurityHelper.getCertificates(config.clusters("test-cluster").certificateAuthority.get)
    caCerts must haveSize(2) // the CA fixture is a bundle of two certificates

    val auth = config.users("test-user").asInstanceOf[CertAuth]
    val clientCerts = SecurityHelper.getCertificates(auth.clientCertificate)
    clientCerts must haveSize(1)
    val privateKey = SecurityHelper.getPrivateKey(auth.clientKey)
    privateKey.getAlgorithm must beEqualTo(expectedKeyAlgorithm)

    // Prove the private key belongs to the certificate by signing with one and verifying with the other
    val data = "hello".getBytes("UTF-8")
    val signer = Signature.getInstance(sigAlgorithm)
    signer.initSign(privateKey)
    signer.update(data)
    val signature = signer.sign()
    val verifier = Signature.getInstance(sigAlgorithm)
    verifier.initVerify(clientCerts.head.getPublicKey)
    verifier.update(data)
    verifier.verify(signature) must beTrue
  }

  "An example kubeconfig file can be parsed correctly" >> {
    val is = new java.io.ByteArrayInputStream(kubeConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
    val k8sConfig = K8SConfiguration.parseKubeconfigStream(is)
    val parsedFromStringConfig = k8sConfig.get
    
    // construct equivalent config directly for comparison
 
    val cowCluster=K8SCluster("v1", "http://cow.org:8080",false)
    val horseCluster=K8SCluster("v1","https://horse.org:4443", false, certificateAuthority=Some(Left("path/to/my/cafile")))
    val pigCluster=K8SCluster("v1", "https://pig.org:443", true)
    val clusters=Map("cow-cluster" -> cowCluster,"horse-cluster"->horseCluster,"pig-cluster"->pigCluster)
    
    val blueUser = TokenAuth("blue-token")
    val greenUser = CertAuth(clientCertificate = Left("path/to/my/client/cert"), clientKey = Left("path/to/my/client/key"), user = None)
    val jwtUser= OidcAuth(idToken = "jwt-token")
    val gcpUser = GcpAuth(accessToken = Some("myAccessToken"), expiry = Some(Instant.parse("2018-03-04T14:08:18Z")),
      cmdPath = "/home/user/google-cloud-sdk/bin/gcloud", cmdArgs = "config config-helper --format=json")
    val noAccessTokenGcpUser = GcpAuth(accessToken = None, expiry = None,
      cmdPath = "/home/user/google-cloud-sdk/bin/gcloud", cmdArgs = "config config-helper --format=json")
    val users=Map("blue-user"->blueUser,"green-user"->greenUser,"jwt-user"->jwtUser, "gke-user"->gcpUser,
      "string-date-gke-user"->gcpUser, "other-date-gke-user" -> noAccessTokenGcpUser)

    val federalContext=K8SContext(horseCluster,greenUser,Namespace.forName("chisel-ns"))
    val queenAnneContext=K8SContext(pigCluster,blueUser, Namespace.forName("saw-ns"))
    val contexts=Map("federal-context"->federalContext,"queen-anne-context"->queenAnneContext)
    
    val directlyConstructedConfig=Configuration(clusters,contexts,federalContext,users)
    directlyConstructedConfig.clusters must beEqualTo(parsedFromStringConfig.clusters)
    directlyConstructedConfig.contexts must beEqualTo(parsedFromStringConfig.contexts)
    directlyConstructedConfig.users must beEqualTo(parsedFromStringConfig.users)
    directlyConstructedConfig.currentContext must beEqualTo(parsedFromStringConfig.currentContext)
    
    directlyConstructedConfig must beEqualTo(parsedFromStringConfig)
  }

  "Parse SEC1 EC private keys from kubeconfig file" >> {
    parseAndVerifyKeys(ecClientCertPem, ecSec1KeyPem, "EC", "SHA256withECDSA")
  }

  "Parse PKCS#8 EC private keys from kubeconfig file" >> {
    parseAndVerifyKeys(ecClientCertPem, ecPkcs8KeyPem, "EC", "SHA256withECDSA")
  }

  "Parse PKCS#1 RSA private keys from kubeconfig file" >> {
    parseAndVerifyKeys(rsaClientCertPem, rsaPkcs1KeyPem, "RSA", "SHA256withRSA")
  }

  "Parse PKCS#8 RSA private keys from kubeconfig file" >> {
    parseAndVerifyKeys(rsaClientCertPem, rsaPkcs8KeyPem, "RSA", "SHA256withRSA")
  }

  "Reject PKCS#8 data under an EC PRIVATE KEY header" >> {
    val misLabelled = ecPkcs8KeyPem.replace("BEGIN PRIVATE KEY", "BEGIN EC PRIVATE KEY").replace("END PRIVATE KEY", "END EC PRIVATE KEY")
    val is = new ByteArrayInputStream(kubeconfigWith(caCertPem, ecClientCertPem, misLabelled).getBytes("UTF-8"))
    val config = K8SConfiguration.parseKubeconfigStream(is).get
    SecurityHelper.getPrivateKey(config.users("test-user").asInstanceOf[CertAuth].clientKey) must throwA[Exception]
  }

  "If kubeconfig is not found at expected path then a Failure is returned" >> {
      import java.nio.file.Paths
      val path=Paths.get("file:///doesNotExist")
      val parsed = Configuration.parseKubeconfigFile(path)
      parsed.isFailure must beTrue
  }

  "if a relative path and directory are specified, then the parsed config must contain the fully expanded paths" >> {
    val is = new java.io.ByteArrayInputStream(kubeConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
    val k8sConfig = K8SConfiguration.parseKubeconfigStream(is, Some(Paths.get("/top/level/path")))
    val parsedFromStringConfig = k8sConfig.get
    val clientCertificate = parsedFromStringConfig.users("green-user").asInstanceOf[CertAuth].clientCertificate
    clientCertificate must beLeft("/top/level/path/path/to/my/client/cert")
  }

  "ignore missing cluster and user references" >> {
    val config = """
apiVersion: v1
kind: Config
current-context: bad-context
preferences: {}
clusters: []
contexts:
- context:
    cluster: not-existent
    namespace: default
    user: also-not-existent
  name: bad-context
users: []
"""
    val stream = new ByteArrayInputStream(config.getBytes("utf-8"))
    val parsed = K8SConfiguration.parseKubeconfigStream(stream).get
    parsed.contexts.size should beEqualTo(1)
    parsed.currentContext.authInfo should beEqualTo(NoAuth)
  }
}