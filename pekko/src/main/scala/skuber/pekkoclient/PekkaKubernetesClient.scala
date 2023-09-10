package skuber.pekkoclient

import play.api.libs.json.Format
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.util.ByteString

import scala.concurrent.{Future, Promise}

import skuber.api.client.{KubernetesClient, ListOptions, LoggingContext, WatchEvent}
import skuber.model.{ObjectResource, Pod, ResourceDefinition}


/**
  * This trait extends the generic KubernetesClient trait with streaming operations. These streaming operations
  * have signatures that have types from the underlying streaming library, in this case Akka streams.
  * This is the interface for any skuber client that is returned by `skuber.akkaclient.k8sInit` calls
  */
trait PekkoKubernetesClient extends KubernetesClient {
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

  // The Watch methods place a Watch on the specified resource on the Kubernetes cluster.
  // The methods return Akka streams sources that will reactively emit a stream of updated
  // values of the watched resources.

  def watch[O <: ObjectResource](obj: O)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]]

  // The Watch methods place a Watch on the specified resource on the Kubernetes cluster.
  // The methods return Akka streams sources that will reactively emit a stream of updated
  // values of the watched resources.

  def watch[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]]

  // watch events on all objects of specified kind in current namespace
  def watchAll[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]]

  def watchContinuously[O <: ObjectResource](obj: O)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

  def watchContinuously[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, errorHandler: Option[String => _] = None)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

  def watchAllContinuously[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, errorHandler: Option[String => _] = None)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]

  def watchWithOptions[O <: ObjectResource](options: ListOptions, bufsize: Int = 10000, errorHandler: Option[String => _] = None)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _]
}
