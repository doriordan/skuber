package skuber.api

import skuber._

import org.specs2.mutable.Specification
import org.specs2.execute.Result
import org.specs2.execute.Failure
import org.specs2.execute.Success

/**
 * @author David O'Riordan
 */
class ConfigurationSpec extends Specification {
  "An example kubeconfig file can be parsed correctly" >> {
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
"""
    val is = new java.io.ByteArrayInputStream(kubeConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
    val k8sConfig = K8SConfiguration.parseKubeconfigStream(is)
    val parsedFromStringConfig = k8sConfig.get
    
    // construct equivalent config directly for comparison
 
    val cowCluster=K8SCluster("v1", "http://cow.org:8080",false)
    val horseCluster=K8SCluster("v1","https://horse.org:4443", false, certificateAuthority=Some(Left("path/to/my/cafile")))
    val pigCluster=K8SCluster("v1", "https://pig.org:443", true)
    val clusters=Map("cow-cluster" -> cowCluster,"horse-cluster"->horseCluster,"pig-cluster"->pigCluster)
    
    val blueUser=K8SAuthInfo(token=Some("blue-token"))
    val greenUser=K8SAuthInfo(clientCertificate=Some(Left("path/to/my/client/cert")), clientKey=Some(Left("path/to/my/client/key")))
    val jwtUser=K8SAuthInfo(jwt = Some("jwt-token"))
    val users=Map("blue-user"->blueUser,"green-user"->greenUser,"jwt-user"->jwtUser)

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
  
}