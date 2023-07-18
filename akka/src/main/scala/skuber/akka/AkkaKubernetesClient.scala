package skuber.akka

import skuber.api.client.KubernetesClient
import skuber.model.Pod

import scala.concurrent.{Future, Promise}

trait AkkaKubernetesClient extends KubernetesClient {
  /**
    * Get the logs from a pod (similar to `kubectl logs ...`). The logs are streamed using an Akka streams source
    *
    * @param name        the name of the pod
    * @param queryParams optional parameters of the request (for example container name)
    * @param namespace   if set this specifies the namespace of the pod (otherwise the configured namespace is used)
    * @return A future containing a Source for the logs stream.
    */
  def getPodLogSource(name: String, queryParams: Pod.LogQueryParams, namespace: Option[String] = None)(implicit lc: LoggingContext): Future[Source[ByteString, _]]

  /**
    * Execute a command in a pod (similar to `kubectl exec ...`)
    *
    * @param podName            the name of the pod
    * @param command            the command to execute
    * @param maybeContainerName an optional container name
    * @param maybeStdin         optional Akka Source for sending input to stdin for the command
    * @param maybeStdout        optional Akka Sink to receive output from stdout for the command
    * @param maybeStderr        optional Akka Sink to receive output from stderr for the command
    * @param tty                optionally set tty on
    * @param maybeClose         if set, this can be used to close the connection to the pod by completing the promise
    * @return A future indicating the exec command has been submitted
    */
  def exec(
    podName: String,
    command: Seq[String],
    maybeContainerName: Option[String] = None,
    maybeStdin: Option[Source[String, _]] = None,
    maybeStdout: Option[Sink[String, _]] = None,
    maybeStderr: Option[Sink[String, _]] = None,
    tty: Boolean = false,
    maybeClose: Option[Promise[Unit]] = None)(implicit lc: LoggingContext): Future[Unit]

}
