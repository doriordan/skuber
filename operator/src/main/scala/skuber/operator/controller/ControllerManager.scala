package skuber.operator.controller

import org.apache.pekko.actor.ActorSystem
import play.api.libs.json.Format
import skuber.model.{ObjectResource, ResourceDefinition}
import skuber.operator.cache.{PekkoSharedCache, ReflectorConfig, SharedCache}
import skuber.operator.leaderelection.{LeaderElection, LeaderElectionConfig}
import skuber.operator.reconciler.OperatorLogger
import skuber.pekkoclient.PekkoKubernetesClient

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

/**
 * Configuration for the ControllerManager.
 */
case class ManagerConfig(
  /** Namespace to watch. None means all namespaces. */
  namespace: Option[String] = None,

  /** Leader election configuration. None disables leader election. */
  leaderElection: Option[LeaderElectionConfig] = None,

  /** Reflector configuration for cache. */
  reflectorConfig: ReflectorConfig = ReflectorConfig.default,

  /** Timeout for waiting for cache sync on startup. */
  cacheSyncTimeout: FiniteDuration = 2.minutes,

  /** Periodic resync interval. Set to None to disable. */
  resyncPeriod: Option[FiniteDuration] = Some(10.hours)
)

object ManagerConfig:
  val default: ManagerConfig = ManagerConfig()

/**
 * ControllerManager orchestrates multiple controllers.
 *
 * It manages:
 * - Shared cache for all resource types
 * - Leader election (optional)
 * - Controller lifecycle
 *
 * Usage:
 * {{{
 * val manager = ControllerManager(config, client)
 *
 * manager.add(controller1)
 * manager.add(controller2)
 *
 * manager.start() // Starts cache, leader election, and controllers
 * }}}
 */
trait ControllerManager:

  /** Shared Kubernetes client */
  def client: PekkoKubernetesClient

  /** Shared cache for all resource types */
  def cache: SharedCache

  /** ActorSystem for streaming */
  def actorSystem: ActorSystem

  /** Register a controller with the manager */
  def add[R <: ObjectResource](controller: Controller[R]): Unit

  /** Start all controllers (waits for cache sync first) */
  def start(): Future[Unit]

  /** Stop all controllers gracefully */
  def stop(): Future[Unit]

  /** Check if this instance is the leader (or if leader election is disabled) */
  def isLeader: Boolean

  /** Check if the manager is running */
  def isRunning: Boolean

object ControllerManager:
  /**
   * Create a new ControllerManager.
   */
  def apply(config: ManagerConfig, client: PekkoKubernetesClient)(
    using system: ActorSystem
  ): ControllerManager =
    new PekkoControllerManager(config, client)

/**
 * Pekko-based implementation of ControllerManager.
 */
class PekkoControllerManager(
  config: ManagerConfig,
  val client: PekkoKubernetesClient
)(using val actorSystem: ActorSystem) extends ControllerManager:

  given ExecutionContext = actorSystem.dispatcher

  private val log = OperatorLogger("skuber.operator.controller.Manager")

  val cache: SharedCache = new PekkoSharedCache(
    client,
    config.reflectorConfig,
    config.namespace
  )

  private val controllers = ListBuffer[Controller[?]]()
  private var leaderElection: Option[LeaderElection] = None

  @volatile private var _isLeader: Boolean = config.leaderElection.isEmpty
  @volatile private var _isRunning: Boolean = false

  def isLeader: Boolean = _isLeader
  def isRunning: Boolean = _isRunning

  def add[R <: ObjectResource](controller: Controller[R]): Unit =
    if _isRunning then
      throw new IllegalStateException("Cannot add controllers after manager has started")
    controllers += controller

  def start(): Future[Unit] =
    log.info("Starting controller manager")

    config.leaderElection match
      case Some(leConfig) =>
        log.info(s"Leader election enabled with lease ${leConfig.leaseName}")
        leaderElection = Some(new LeaderElection(client, leConfig))
        leaderElection.get.run(
          onStartedLeading = () => startControllersInternal(),
          onStoppedLeading = () => stopControllersInternal()
        )

      case None =>
        log.info("Leader election disabled, starting immediately")
        startControllersInternal()

  private def startControllersInternal(): Future[Unit] =
    _isLeader = true
    _isRunning = true

    for
      // Start cache and wait for initial sync
      _ <- cache.start()
      _ = log.info("Cache started, waiting for sync")

      synced <- cache.waitForSync(config.cacheSyncTimeout)
      _ = if !synced then
        throw new RuntimeException(s"Cache sync timeout after ${config.cacheSyncTimeout}")
      _ = log.info("Cache synced")

      // Start all controllers
      _ <- Future.traverse(controllers.toList)(_.start())
      _ = log.info(s"Started ${controllers.size} controller(s)")
    yield ()

  private def stopControllersInternal(): Future[Unit] =
    log.info("Stopping controllers")
    _isLeader = false

    Future.traverse(controllers.toList)(_.stop()).map(_ => ())

  def stop(): Future[Unit] =
    log.info("Stopping controller manager")
    _isRunning = false

    for
      _ <- leaderElection.map(_.stop()).getOrElse(Future.unit)
      _ <- stopControllersInternal()
      _ <- cache.stop()
      _ = log.info("Controller manager stopped")
    yield ()
