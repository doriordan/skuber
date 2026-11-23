package skuber.api.kubeconfig

import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.Base64

import org.specs2.mutable.Specification
import play.api.libs.json.Json

class ExecCredentialRunnerSpec extends Specification {

  private def exec(
    command: String,
    args: List[String] = Nil,
    env: List[RawExecEnvVar] = Nil,
    apiVersion: String = "client.authentication.k8s.io/v1beta1",
    installHint: Option[String] = None,
    provideClusterInfo: Boolean = false,
    interactiveMode: String = "IfAvailable"
  ): RawExecConfig = RawExecConfig(command, args, env, apiVersion, installHint, provideClusterInfo, interactiveMode)

  private def shell(script: String): RawExecConfig = exec("/bin/sh", List("-c", script))

  "ExecCredentialRunner.run" should {

    "parse a successful token response with an expiry" >> {
      val result = ExecCredentialRunner.run(
        shell("""echo '{"status":{"token":"tok-123","expirationTimestamp":"2099-01-01T00:00:00Z"}}'"""),
        None
      )
      result must beSuccessfulTry(ExecToken("tok-123", Some(Instant.parse("2099-01-01T00:00:00Z"))))
    }

    "parse a successful token response with no expiry" >> {
      val result = ExecCredentialRunner.run(shell("""echo '{"status":{"token":"tok-123"}}'"""), None)
      result must beSuccessfulTry(ExecToken("tok-123", None))
    }

    "parse a successful client-certificate response (Teleport's actual shape)" >> {
      val certB64 = Base64.getEncoder.encodeToString("fake-cert-pem".getBytes("UTF-8"))
      val keyB64 = Base64.getEncoder.encodeToString("fake-key-pem".getBytes("UTF-8"))
      val result = ExecCredentialRunner.run(
        shell(s"""echo '{"status":{"clientCertificateData":"$certB64","clientKeyData":"$keyB64"}}'"""),
        None
      )
      result must beSuccessfulTry.like { case ExecClientCert(cert, key) =>
        new String(cert, "UTF-8") must beEqualTo("fake-cert-pem")
        new String(key, "UTF-8") must beEqualTo("fake-key-pem")
      }
    }

    "fail with the exit code and stderr when the plugin exits non-zero" >> {
      val result = ExecCredentialRunner.run(shell("echo 'boom' 1>&2; exit 3"), None)
      result must beFailedTry.like { case e => e.getMessage must (contain("exited with code 3") and contain("boom")) }
    }

    "fail clearly when the plugin's output isn't valid JSON" >> {
      val result = ExecCredentialRunner.run(shell("echo 'not json'"), None)
      result must beFailedTry.like { case e => e.getMessage must contain("valid JSON") }
    }

    "fail clearly when the plugin's output has no 'status' field" >> {
      val result = ExecCredentialRunner.run(shell("""echo '{"apiVersion":"v1","kind":"ExecCredential"}'"""), None)
      result must beFailedTry.like { case e => e.getMessage must contain("no 'status' field") }
    }

    "fail clearly when the plugin's status has neither a token nor a cert" >> {
      val result = ExecCredentialRunner.run(shell("""echo '{"status":{}}'"""), None)
      result must beFailedTry.like { case e => e.getMessage must contain("neither a token nor a client certificate") }
    }

    "fail with the installHint when the command can't be found" >> {
      val result = ExecCredentialRunner.run(
        exec("this-command-does-not-exist-xyz", installHint = Some("brew install this-command")),
        None
      )
      result must beFailedTry.like { case e => e.getMessage must contain("brew install this-command") }
    }

    "fail fast without spawning anything when interactiveMode is Always" >> {
      val markerFile = Files.createTempFile("exec-marker", ".txt")
      Files.delete(markerFile)
      val result = ExecCredentialRunner.run(shell(s"touch $markerFile").copy(interactiveMode = "Always"), None)
      result must beFailedTry.like { case e => e.getMessage must contain("interactiveMode=Always") }
      Files.exists(markerFile) must beFalse
    }

    "pass apiVersion, spec.interactive=false and (when requested) cluster info via KUBERNETES_EXEC_INFO" >> {
      val captureFile: Path = Files.createTempFile("exec-info-capture", ".json")

      val withoutClusterInfo = ExecCredentialRunner.run(
        shell(s"""printf '%s' "$$KUBERNETES_EXEC_INFO" > $captureFile; echo '{"status":{"token":"ok"}}'"""),
        None
      )
      withoutClusterInfo must beSuccessfulTry
      val requestWithoutCluster = Json.parse(Files.readAllBytes(captureFile))
      (requestWithoutCluster \ "apiVersion").as[String] must beEqualTo("client.authentication.k8s.io/v1beta1")
      (requestWithoutCluster \ "spec" \ "interactive").as[Boolean] must beFalse
      (requestWithoutCluster \ "spec" \ "cluster").toOption must beNone

      val clusterInfo = ExecClusterInfo("https://my-cluster.example:6443", Some(Base64.getEncoder.encodeToString("ca-bytes".getBytes)), insecureSkipTLSVerify = true)
      val withClusterInfo = ExecCredentialRunner.run(
        shell(s"""printf '%s' "$$KUBERNETES_EXEC_INFO" > $captureFile; echo '{"status":{"token":"ok"}}'"""),
        Some(clusterInfo)
      )
      withClusterInfo must beSuccessfulTry
      val requestWithCluster = Json.parse(Files.readAllBytes(captureFile))
      (requestWithCluster \ "spec" \ "cluster" \ "server").as[String] must beEqualTo("https://my-cluster.example:6443")
      (requestWithCluster \ "spec" \ "cluster" \ "insecure-skip-tls-verify").as[Boolean] must beTrue
      (requestWithCluster \ "spec" \ "cluster" \ "certificate-authority-data").asOpt[String] must beSome
    }

    "pass the exec config's extra env vars through to the plugin" >> {
      val result = ExecCredentialRunner.run(
        exec("/bin/sh", List("-c", """echo "{\"status\":{\"token\":\"$MY_VAR\"}}""""), env = List(RawExecEnvVar("MY_VAR", "from-env"))),
        None
      )
      result must beSuccessfulTry(ExecToken("from-env", None))
    }
  }
}
