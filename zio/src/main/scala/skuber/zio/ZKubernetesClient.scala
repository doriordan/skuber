package skuber.zio

import zio.*
import zio.stream.*
import play.api.libs.json.{Format, Writes}
import skuber.api.client.{K8SException, WatchEvent, WatchParameters, ListOptions, DeleteOptions}
import skuber.api.patch.Patch
import skuber.model.*

trait ZKubernetesClient:
  def get[O <: ObjectResource](name: String)(using Format[O], ResourceDefinition[O]): IO[K8SException, O]
  def getOption[O <: ObjectResource](name: String)(using Format[O], ResourceDefinition[O]): IO[K8SException, Option[O]]
  def create[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O]): IO[K8SException, O]
  def update[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O]): IO[K8SException, O]
  def delete[O <: ObjectResource](name: String, gracePeriodSeconds: Int = -1)(using ResourceDefinition[O]): IO[K8SException, Unit]
  def deleteWithOptions[O <: ObjectResource](name: String, options: DeleteOptions)(using ResourceDefinition[O]): IO[K8SException, Unit]
  def list[L <: KList[?]]()(using Format[L], ResourceDefinition[L]): IO[K8SException, L]
  def listSelected[L <: KList[?]](labelSelector: LabelSelector)(using Format[L], ResourceDefinition[L]): IO[K8SException, L]
  def listWithOptions[L <: KList[?]](options: ListOptions)(using Format[L], ResourceDefinition[L]): IO[K8SException, L]
  def updateStatus[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O], HasStatusSubresource[O]): IO[K8SException, O]
  def getScale[O <: ObjectResource](name: String)(using ResourceDefinition[O], Scale.SubresourceSpec[O]): IO[K8SException, Scale]
  def updateScale[O <: ObjectResource](name: String, scale: Scale)(using ResourceDefinition[O], Scale.SubresourceSpec[O]): IO[K8SException, Scale]
  def patch[P <: Patch, O <: ObjectResource](name: String, patchData: P, namespace: Option[String] = None)(using Writes[P], Format[O], ResourceDefinition[O]): IO[K8SException, O]
  def watch[O <: ObjectResource](params: WatchParameters = WatchParameters())(using Format[O], ResourceDefinition[O]): ZStream[Any, K8SException, WatchEvent[O]]
  def getPodLogStream(name: String, queryParams: Pod.LogQueryParams = Pod.LogQueryParams(), namespace: Option[String] = None): ZStream[Any, Throwable, Byte]
  def exec(podName: String, command: Seq[String], containerName: Option[String] = None, stdin: Option[ZStream[Any, Nothing, String]] = None, tty: Boolean = false): ZStream[Any, Throwable, ExecOutput]
  def getServerAPIVersions: IO[K8SException, List[String]]
  def usingNamespace(namespace: String): ZKubernetesClient

object ZKubernetesClient:

  val layer: ZLayer[Any, Throwable, ZKubernetesClient] =
    ZLayer.scoped(scoped)

  def layer(config: skuber.api.Configuration): ZLayer[Any, Throwable, ZKubernetesClient] =
    ZLayer.scoped(scoped(config))

  val scoped: ZIO[Scope, Throwable, ZKubernetesClient] =
    ZIO.attempt(skuber.api.Configuration.defaultK8sConfig).flatMap(scoped(_))

  def scoped(config: skuber.api.Configuration): ZIO[Scope, Throwable, ZKubernetesClient] =
    for
      client    <- skuber.zio.internal.ZioTlsHelper.buildClient(config)
      k8sClient <- skuber.zio.internal.ZKubernetesClientImpl.acquire(config)
                     .provideEnvironment(ZEnvironment(client))
    yield k8sClient
