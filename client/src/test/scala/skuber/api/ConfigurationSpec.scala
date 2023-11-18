package skuber.api

import java.nio.file.Paths
import java.time.Instant
import org.apache.pekko.actor.ActorSystem
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import skuber._
import skuber.api.client._
import skuber.api.client.token.ExecAuthRefreshable
import scala.util.{Success, Try}

/**
  * @author David O'Riordan
  */
class ConfigurationSpec extends AnyFunSpec with Matchers {


  implicit val system: ActorSystem = ActorSystem("test")
  implicit val loggingContext: LoggingContext = new LoggingContext {
    override def output: String = "test"
  }
  describe("Test kube config") {
    it("An example kubeconfig file can be parsed correctly") {
      val is = new java.io.ByteArrayInputStream(TestData.kubeConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
      val k8sConfig = K8SConfiguration.parseKubeconfigStream(is)
      val parsedFromStringConfig = k8sConfig.get

      // construct equivalent config directly for comparison

      val cowCluster = K8SCluster("v1", "http://cow.org:8080", false, clusterName = Some("cow-cluster"))
      val horseCluster = K8SCluster("v1", "https://horse.org:4443", false, certificateAuthority = Some(Left("path/to/my/cafile")), clusterName = Some("horse-cluster"))
      val pigCluster = K8SCluster("v1", "https://pig.org:443", true, clusterName = Some("pig-cluster"))
      val clusters = Map("cow-cluster" -> cowCluster, "horse-cluster" -> horseCluster, "pig-cluster" -> pigCluster)

      val blueUser = TokenAuth("blue-token")
      val greenUser = CertAuth(clientCertificate = Left("path/to/my/client/cert"), clientKey = Left("path/to/my/client/key"), user = None)
      val jwtUser = OidcAuth(idToken = "jwt-token")
      val gcpUser = GcpAuth(accessToken = Some("myAccessToken"), expiry = Some(Instant.parse("2018-03-04T14:08:18Z")),
        cmdPath = "/home/user/google-cloud-sdk/bin/gcloud", cmdArgs = "config config-helper --format=json")
      val noAccessTokenGcpUser = GcpAuth(accessToken = None, expiry = None,
        cmdPath = "/home/user/google-cloud-sdk/bin/gcloud", cmdArgs = "config config-helper --format=json")
      val users = Map("blue-user" -> blueUser, "green-user" -> greenUser, "jwt-user" -> jwtUser, "gke-user" -> gcpUser,
        "string-date-gke-user" -> gcpUser, "other-date-gke-user" -> noAccessTokenGcpUser)

      val federalContext = K8SContext(horseCluster, greenUser, Namespace.forName("chisel-ns"))
      val queenAnneContext = K8SContext(pigCluster, blueUser, Namespace.forName("saw-ns"))
      val contexts = Map("federal-context" -> federalContext, "queen-anne-context" -> queenAnneContext)

      val directlyConstructedConfig = Configuration(clusters, contexts, federalContext, users)
      directlyConstructedConfig.clusters shouldBe parsedFromStringConfig.clusters
      directlyConstructedConfig.contexts shouldBe parsedFromStringConfig.contexts
      directlyConstructedConfig.users shouldBe parsedFromStringConfig.users
      directlyConstructedConfig.currentContext shouldBe parsedFromStringConfig.currentContext

      directlyConstructedConfig shouldBe parsedFromStringConfig
    }

    it("Parse EC private keys from kubeconfig file") {

      val is = new java.io.ByteArrayInputStream(TestData.ecConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
      val k8sConfig = K8SConfiguration.parseKubeconfigStream(is).get
      Try(k8sInit(k8sConfig)) shouldBe a[Success[KubernetesClient]]
    }

    it("Parse RSA private keys from kubeconfig file") {
      val is = new java.io.ByteArrayInputStream(TestData.ecConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
      val k8sConfig = K8SConfiguration.parseKubeconfigStream(is).get
      Try(k8sInit(k8sConfig)) shouldBe a[Success[KubernetesClient]]
    }

    it("Parse PKCS#8 private keys from kubeconfig file") {

      val is = new java.io.ByteArrayInputStream(TestData.pkcs8str.getBytes(java.nio.charset.Charset.forName("UTF-8")))
      val k8sConfig = K8SConfiguration.parseKubeconfigStream(is).get
      Try(k8sInit(k8sConfig)) shouldBe a[Success[KubernetesClient]]
    }

    it("If kubeconfig is not found at expected path then a Failure is returned") {
      import java.nio.file.Paths
      val path = Paths.get("file:///doesNotExist")
      val parsed = Configuration.parseKubeconfigFile(path)
      parsed.isFailure shouldBe true
    }

    it("if a relative path and directory are specified, then the parsed config must contain the fully expanded paths") {
      val is = new java.io.ByteArrayInputStream(TestData.kubeConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
      val k8sConfig = K8SConfiguration.parseKubeconfigStream(is, Some(Paths.get("/top/level/path")))
      val parsedFromStringConfig = k8sConfig.get
      val clientCertificate = parsedFromStringConfig.users("green-user").asInstanceOf[CertAuth].clientCertificate
      clientCertificate shouldBe Left("/top/level/path/path/to/my/client/cert")
    }

    it("Parse exec kubeconfig file") {
      val is = new java.io.ByteArrayInputStream(TestData.execConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))

      val k8sConfig = K8SConfiguration.parseKubeconfigStream(is).get
      k8sConfig.currentContext.authInfo shouldBe a[ExecAuthRefreshable]
      k8sConfig.currentContext.authInfo match {
        case exec: ExecAuthRefreshable =>
          exec.config.args shouldBe List("--region", "us-east-1", "eks", "get-token", "--cluster-name", "test-cluster", "--output", "json")
          exec.config.cmd shouldBe "aws"
          exec.config.envVariables shouldBe Map("AWS_PROFILE" -> "default")
        case _ => fail("should be ExecAuthRefreshable")
      }
    }


  }
}