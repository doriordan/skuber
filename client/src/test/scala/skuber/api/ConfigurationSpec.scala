package skuber.api

import skuber._
import org.specs2.mutable.Specification
import java.nio.file.Paths
import java.time.Instant

import skuber.api.client._

/**
 * @author David O'Riordan
 */
class ConfigurationSpec extends Specification {
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
"""
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
    val gcpUser = GcpAuth(accessToken = "myAccessToken", expiry = Instant.parse("2018-03-04T14:08:18Z"),
      cmdPath = "/home/user/google-cloud-sdk/bin/gcloud", cmdArgs = "config config-helper --format=json")
    val users=Map("blue-user"->blueUser,"green-user"->greenUser,"jwt-user"->jwtUser, "gke-user"->gcpUser)

    val federalContext=K8SContext(horseCluster,greenUser,Namespace.forName("chisel-ns"))
    val queenAnneContext=K8SContext(pigCluster,blueUser, Namespace.forName("saw-ns"))
    val contexts=Map("federal-context"->federalContext,"queen-anne-context"->queenAnneContext)
    
    val directlyConstructedConfig=Configuration(clusters,contexts,federalContext,users)
    directlyConstructedConfig.clusters mustEqual parsedFromStringConfig.clusters
    directlyConstructedConfig.contexts mustEqual parsedFromStringConfig.contexts
    directlyConstructedConfig.users mustEqual parsedFromStringConfig.users
    directlyConstructedConfig.currentContext mustEqual parsedFromStringConfig.currentContext
    
    directlyConstructedConfig mustEqual parsedFromStringConfig
  }

  "If kubeconfig is not found at expected path then a Failure is returned" >> {
      import java.nio.file.Paths
      val path=Paths.get("file:///doesNotExist")
      val parsed = Configuration.parseKubeconfigFile(path)
      parsed.isFailure mustEqual true
  }

  "if a relative path and directory are specfied, then the parsed config must contain the fully expanded paths" >> {
    val is = new java.io.ByteArrayInputStream(kubeConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
    val k8sConfig = K8SConfiguration.parseKubeconfigStream(is, Some(Paths.get("/top/level/path")))
    val parsedFromStringConfig = k8sConfig.get
    val clientCertificate = parsedFromStringConfig.users("green-user").asInstanceOf[CertAuth].clientCertificate
    clientCertificate mustEqual Left("/top/level/path/path/to/my/client/cert")
  }
}