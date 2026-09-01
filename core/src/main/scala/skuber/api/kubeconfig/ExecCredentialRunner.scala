package skuber.api.kubeconfig

import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try
import scala.util.control.NonFatal

/**
 * Runs a kubeconfig `exec`-configured credential plugin (e.g. `aws eks get-token`,
 * `gke-gcloud-auth-plugin`, `tsh kube credentials`) and parses its response, per the
 * `client.authentication.k8s.io` v1/v1beta1 protocol (see [[ExecCredentialProtocol]]).
 *
 * This is purely synchronous/blocking - callers (see `KubeconfigConverter`) are responsible for
 * running it off whatever thread pool is appropriate for a blocking subprocess call.
 *
 * Note: no timeout is imposed on the child process. A misconfigured or hanging plugin (e.g. one
 * waiting on an interactive prompt it will never receive) will block the calling thread/Future
 * indefinitely. This is a known limitation.
 */
private[kubeconfig] object ExecCredentialRunner {

  def run(exec: RawExecConfig, clusterInfo: Option[ExecClusterInfo]): Try[ExecCredentialResult] = Try {
    if (exec.interactiveMode == "Always") {
      sys.error(
        s"credential plugin '${exec.command}' requires interactiveMode=Always but skuber cannot provide an interactive terminal"
      )
    }

    val requestJson = ExecCredentialProtocol.buildRequestJson(exec.apiVersion, clusterInfo)
    val extraEnv = exec.env.map(e => e.name -> e.value) :+ ("KUBERNETES_EXEC_INFO" -> requestJson)
    val commandLine = exec.command +: exec.args

    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val logger = ProcessLogger(
      line => { stdout.append(line).append('\n'); () },
      line => { stderr.append(line).append('\n'); () }
    )

    val exitCode =
      try {
        // connectInput defaults to false here (no ProcessIO/#!< used), so the child's stdin is
        // simply closed/empty rather than inherited - appropriate since skuber always declares
        // itself non-interactive (spec.interactive=false) regardless of interactiveMode.
        Process(commandLine, cwd = None, extraEnv: _*).run(logger).exitValue()
      } catch {
        case NonFatal(e) =>
          val hint = exec.installHint.map(h => s" ($h)").getOrElse("")
          throw new RuntimeException(s"failed to execute credential plugin '${exec.command}'$hint: ${e.getMessage}", e)
      }

    if (exitCode != 0) {
      sys.error(s"credential plugin '${exec.command}' exited with code $exitCode: ${stderr.toString.trim}")
    }

    ExecCredentialProtocol.parseResponse(stdout.toString)
  }
}
