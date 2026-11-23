package skuber.api.kubeconfig

import java.time.Instant

import play.api.libs.json._

import scala.util.control.NonFatal

/**
 * Wire format for the `client.authentication.k8s.io` exec credential plugin protocol - structurally
 * identical between `v1` (https://kubernetes.io/docs/reference/config-api/client-authentication.v1/) and
 * `v1beta1` (https://kubernetes.io/docs/reference/config-api/client-authentication.v1beta1/), so a single
 * implementation handles both (the `apiVersion` string used is simply whatever the kubeconfig's
 * `exec.apiVersion` says, echoed back in the request so the plugin knows which schema to reply with).
 */
private[kubeconfig] final case class ExecClusterInfo(
  server: String,
  certificateAuthorityDataBase64: Option[String],
  insecureSkipTLSVerify: Boolean
)

private[kubeconfig] sealed trait ExecCredentialResult
private[kubeconfig] final case class ExecToken(token: String, expiresAt: Option[Instant]) extends ExecCredentialResult
private[kubeconfig] final case class ExecClientCert(certificatePem: Array[Byte], keyPem: Array[Byte]) extends ExecCredentialResult

private[kubeconfig] object ExecCredentialProtocol {

  /**
   * Builds the JSON that is passed to the plugin via the `KUBERNETES_EXEC_INFO` environment variable:
   * an `ExecCredential` object with an empty `status` and a `spec` describing this (non-interactive)
   * invocation, plus cluster connection info when the exec config asked for it (`provideClusterInfo: true`).
   */
  def buildRequestJson(apiVersion: String, clusterInfo: Option[ExecClusterInfo]): String = {
    val clusterJson: Option[JsObject] = clusterInfo.map { ci =>
      JsObject(
        Seq(
          "server" -> Json.toJson(ci.server),
          "insecure-skip-tls-verify" -> Json.toJson(ci.insecureSkipTLSVerify)
        ) ++ ci.certificateAuthorityDataBase64.map(d => "certificate-authority-data" -> Json.toJson(d)).toSeq
      )
    }
    val spec = JsObject(Seq("interactive" -> Json.toJson(false)) ++ clusterJson.map(c => "cluster" -> (c: JsValue)).toSeq)
    val request = Json.obj(
      "apiVersion" -> apiVersion,
      "kind" -> "ExecCredential",
      "spec" -> spec
    )
    Json.stringify(request)
  }

  /**
   * Parses the plugin's stdout. Deliberately not expressed as a `Reads[ExecCredentialResult]`: we want
   * precise, human-readable failure messages (missing `status`, neither token nor cert present) rather
   * than a generic `JsError`.
   */
  def parseResponse(stdout: String): ExecCredentialResult = {
    val json =
      try Json.parse(stdout)
      catch { case NonFatal(e) => sys.error(s"credential plugin returned output that isn't valid JSON: ${e.getMessage}") }
    val status = (json \ "status").toOption.getOrElse(sys.error("credential plugin returned no 'status' field"))

    val token = (status \ "token").asOpt[String]
    val expiresAt = (status \ "expirationTimestamp").toOption.flatMap(KubeconfigInstant.parseLenient)
    val certData = (status \ "clientCertificateData").asOpt[String]
    val keyData = (status \ "clientKeyData").asOpt[String]

    (token, certData, keyData) match {
      case (Some(t), _, _) => ExecToken(t, expiresAt)
      case (None, Some(c), Some(k)) => ExecClientCert(base64Decode(c), base64Decode(k))
      case _ => sys.error("credential plugin returned neither a token nor a client certificate/key pair in its status")
    }
  }

  private def base64Decode(s: String): Array[Byte] = java.util.Base64.getDecoder.decode(s)
}
