package skuber.catseffect

import cats.effect.{Async, Resource}
import fs2.Stream
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.jdkhttpclient.JdkWSClient
import play.api.libs.json.{Format, Writes}
import skuber.api.Configuration
import skuber.api.client.{K8SException, LoggingConfig, LoggingContext, WatchEvent, WatchParameters, ListOptions, DeleteOptions, ApplyOptions}
import skuber.api.patch.Patch
import skuber.model.ac.ApplyConfiguration
import skuber.internal.TlsHelper
import skuber.catseffect.internal.CatsKubernetesClientImpl
import skuber.catseffect.internal.http4s.Http4sBackend
import skuber.model.*

import scala.concurrent.duration.*

trait CatsKubernetesClient[F[_]]:
  def get[O <: ObjectResource](name: String)(using Format[O], ResourceDefinition[O], LoggingContext): F[Either[K8SException, O]]
  def getOption[O <: ObjectResource](name: String)(using Format[O], ResourceDefinition[O], LoggingContext): F[Option[O]]
  def create[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O], LoggingContext): F[Either[K8SException, O]]
  def update[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O], LoggingContext): F[Either[K8SException, O]]
  def delete[O <: ObjectResource](name: String, gracePeriodSeconds: Int = -1)(using ResourceDefinition[O], LoggingContext): F[Either[K8SException, Unit]]
  def deleteWithOptions[O <: ObjectResource](name: String, options: DeleteOptions)(using ResourceDefinition[O], LoggingContext): F[Either[K8SException, Unit]]
  def list[L <: KList[?]]()(using Format[L], ResourceDefinition[L], LoggingContext): F[Either[K8SException, L]]
  def listSelected[L <: KList[?]](labelSelector: LabelSelector)(using Format[L], ResourceDefinition[L], LoggingContext): F[Either[K8SException, L]]
  def listWithOptions[L <: KList[?]](options: ListOptions)(using Format[L], ResourceDefinition[L], LoggingContext): F[Either[K8SException, L]]
  def updateStatus[O <: ObjectResource](obj: O)(using Format[O], ResourceDefinition[O], HasStatusSubresource[O], LoggingContext): F[Either[K8SException, O]]
  def getScale[O <: ObjectResource](name: String)(using ResourceDefinition[O], Scale.SubresourceSpec[O], LoggingContext): F[Either[K8SException, Scale]]
  def updateScale[O <: ObjectResource](name: String, scale: Scale)(using ResourceDefinition[O], Scale.SubresourceSpec[O], LoggingContext): F[Either[K8SException, Scale]]
  def patch[P <: Patch, O <: ObjectResource](name: String, patchData: P, namespace: Option[String] = None)(using Writes[P], Format[O], ResourceDefinition[O], LoggingContext): F[Either[K8SException, O]]
  def apply[O <: ObjectResource, AC <: ApplyConfiguration[O]](applyConfig: AC, options: ApplyOptions)(using Writes[AC], Format[O], ResourceDefinition[O], LoggingContext): F[Either[K8SException, O]]
  def watch[O <: ObjectResource](params: WatchParameters = WatchParameters())(using Format[O], ResourceDefinition[O], LoggingContext): Stream[F, Either[K8SException, WatchEvent[O]]]
  def getWatcher[O <: ObjectResource]: CatsWatcher[F, O]
  def getPodLogStream(name: String, queryParams: Pod.LogQueryParams = Pod.LogQueryParams(), namespace: Option[String] = None)(using LoggingContext): Stream[F, Byte]
  def exec(podName: String, command: Seq[String], containerName: Option[String] = None, stdin: Option[Stream[F, String]] = None, tty: Boolean = false)(using LoggingContext): Stream[F, ExecOutput]
  def usingNamespace(namespace: String): CatsKubernetesClient[F]
  def getServerAPIVersions(using LoggingContext): F[Either[K8SException, List[String]]]

enum ExecOutput:
  case Stdout(data: String)
  case Stderr(data: String)

object CatsKubernetesClient:

  /** Create a client Resource using the default kubeconfig (from env vars, ~/.kube/config, or in-cluster). */
  def resource[F[_]: Async: Network](using LoggingContext): Resource[F, CatsKubernetesClient[F]] =
    Resource.eval(Async[F].delay(Configuration.defaultK8sConfig)).flatMap(resource[F](_))

  /** Create a client Resource from an explicit Configuration. */
  def resource[F[_]: Async: Network](config: Configuration)(using LoggingContext): Resource[F, CatsKubernetesClient[F]] =
    val context = config.currentContext
    val clusterServer = context.cluster.server
    val auth = context.authInfo
    val namespace = context.namespace.name
    val logConfig = LoggingConfig()

    val maybeSslContext = TlsHelper.buildSSLContext(context)

    for
      tlsContextOpt <- Resource.pure(maybeSslContext.map { sslCtx =>
        Network[F].tlsContext.fromSSLContext(sslCtx)
      })

      httpClient <- {
        val builder = EmberClientBuilder.default[F].withTimeout(30.seconds)
        tlsContextOpt match
          case Some(tlsCtx) => builder.withTLSContext(tlsCtx).build
          case None => builder.build
      }

      wsClient <- maybeSslContext match
        case Some(sslCtx) =>
          Resource.eval(Async[F].delay {
            val jdkClient = java.net.http.HttpClient.newBuilder()
              .sslContext(sslCtx)
              .build()
            JdkWSClient[F](jdkClient)
          })
        case None =>
          JdkWSClient.simple[F]

      backend = Http4sBackend[F](httpClient, wsClient)
    yield CatsKubernetesClientImpl[F](backend, clusterServer, auth, namespace, logConfig)
