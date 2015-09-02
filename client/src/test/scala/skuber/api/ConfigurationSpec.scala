package skuber.api

import org.specs2.mutable.Specification // for unit-style testing
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
    user: black-user
  name: queen-anne-context
current-context: federal-context
kind: Config
preferences:
  colors: true
users:
- name: black-user
  user:
    token: blue-token
- name: green-user
  user:
    client-certificate: path/to/my/client/cert
    client-key: path/to/my/client/key
"""
    val is = new java.io.ByteArrayInputStream(kubeConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
    val k8sConfig = Configuration.parseKubeconfigStream(is)
    val config = k8sConfig.get
    config.clusters.size mustEqual 3
    config.contexts.size mustEqual 2
    config.users.size mustEqual 2
  }
  
}