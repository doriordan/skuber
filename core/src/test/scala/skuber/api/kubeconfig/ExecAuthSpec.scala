package skuber.api.kubeconfig

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.Base64

import org.specs2.mutable.Specification
import play.api.libs.json.Json
import skuber.api.Configuration
import skuber.api.client.AsyncAccessTokenAuth

import scala.concurrent.Await
import scala.concurrent.duration._

class ExecAuthSpec extends Specification {

  sequential

  private def writeExecutableScript(content: String): Path = {
    val script = Files.createTempFile("exec-auth-plugin", ".sh")
    Files.write(script, (s"#!/bin/sh\n$content\n").getBytes(UTF_8))
    script.toFile.setExecutable(true)
    script
  }

  private def kubeconfig(execCommand: Path, provideClusterInfo: Boolean = false): String =
    s"""
apiVersion: v1
kind: Config
current-context: test-context
clusters:
- cluster:
    server: https://my-cluster.example:6443
    certificate-authority-data: ${Base64.getEncoder.encodeToString("ca-bytes".getBytes(UTF_8))}
  name: test-cluster
contexts:
- context:
    cluster: test-cluster
    user: test-user
  name: test-context
users:
- name: test-user
  user:
    exec:
      apiVersion: client.authentication.k8s.io/v1beta1
      command: $execCommand
      provideClusterInfo: $provideClusterInfo
"""

  private def parse(execCommand: Path, provideClusterInfo: Boolean = false): Configuration =
    Configuration.parseKubeconfigStream(new ByteArrayInputStream(kubeconfig(execCommand, provideClusterInfo).getBytes(UTF_8))).get

  private def accessToken(auth: skuber.api.client.AuthInfo): String =
    Await.result(auth.asInstanceOf[AsyncAccessTokenAuth].accessToken(), 5.seconds)

  "exec-based auth" should {

    "not execute the plugin at parse time (lazy)" >> {
      val counter = Files.createTempFile("exec-auth-counter", ".txt")
      Files.delete(counter)
      val script = writeExecutableScript(s"""echo x >> $counter
echo '{"status":{"token":"tok","expirationTimestamp":"2099-01-01T00:00:00Z"}}'""")

      val config = parse(script)
      Files.exists(counter) must beFalse

      accessToken(config.currentContext.authInfo) must beEqualTo("tok")
      Files.readAllLines(counter).size must beEqualTo(1)
    }

    "cache the token and not re-invoke the plugin before it expires" >> {
      val counter = Files.createTempFile("exec-auth-counter", ".txt")
      Files.delete(counter)
      val script = writeExecutableScript(s"""echo x >> $counter
echo '{"status":{"token":"tok","expirationTimestamp":"2099-01-01T00:00:00Z"}}'""")

      val config = parse(script)
      accessToken(config.currentContext.authInfo) must beEqualTo("tok")
      accessToken(config.currentContext.authInfo) must beEqualTo("tok")
      Files.readAllLines(counter).size must beEqualTo(1)
    }

    "re-invoke the plugin once its returned credential has expired" >> {
      val counter = Files.createTempFile("exec-auth-counter", ".txt")
      Files.delete(counter)
      val script = writeExecutableScript(s"""echo x >> $counter
echo '{"status":{"token":"tok","expirationTimestamp":"2000-01-01T00:00:00Z"}}'""")

      val config = parse(script)
      accessToken(config.currentContext.authInfo) must beEqualTo("tok")
      accessToken(config.currentContext.authInfo) must beEqualTo("tok")
      Files.readAllLines(counter).size must beEqualTo(2)
    }

    "fail clearly (not silently) when the plugin returns a client certificate instead of a token" >> {
      val certB64 = Base64.getEncoder.encodeToString("fake-cert-pem".getBytes(UTF_8))
      val keyB64 = Base64.getEncoder.encodeToString("fake-key-pem".getBytes(UTF_8))
      val script = writeExecutableScript(s"""echo '{"status":{"clientCertificateData":"$certB64","clientKeyData":"$keyB64"}}'""")

      val config = parse(script)
      accessToken(config.currentContext.authInfo) must throwA[UnsupportedOperationException]
    }

    "include the bound cluster's connection info when provideClusterInfo=true, only for the context-bound auth" >> {
      val captureFile = Files.createTempFile("exec-auth-capture", ".json")
      val script = writeExecutableScript(s"""printf '%s' "$$KUBERNETES_EXEC_INFO" > $captureFile
echo '{"status":{"token":"tok","expirationTimestamp":"2099-01-01T00:00:00Z"}}'""")

      val config = parse(script, provideClusterInfo = true)

      accessToken(config.currentContext.authInfo) must beEqualTo("tok")
      val contextRequest = Json.parse(Files.readAllBytes(captureFile))
      (contextRequest \ "spec" \ "cluster" \ "server").as[String] must beEqualTo("https://my-cluster.example:6443")

      // The flat top-level `users` map entry for the same user has no associated cluster (it isn't
      // bound to one until a context pairs it with a cluster), so its own, independent lazy AuthInfo
      // omits spec.cluster entirely when invoked directly.
      accessToken(config.users("test-user")) must beEqualTo("tok")
      val flatUserRequest = Json.parse(Files.readAllBytes(captureFile))
      (flatUserRequest \ "spec" \ "cluster").toOption must beNone
    }
  }
}
