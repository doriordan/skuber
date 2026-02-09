package skuber.operator.cache

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.libs.json.Format
import skuber.model.{ObjectResource, ResourceDefinition}
import skuber.pekkoclient.PekkoKubernetesClient
import skuber.operator.reconciler.OperatorLogger

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

/**
 * Pekko-based implementation of SharedCache.
 * Creates and manages Reflectors for each resource type.
 */
class PekkoSharedCache(
  client: PekkoKubernetesClient,
  config: ReflectorConfig = ReflectorConfig.default,
  watchNamespace: Option[String] = None  // None = all namespaces
)(using system: ActorSystem) extends SharedCache:

  given ExecutionContext = system.dispatcher
  given Materializer = Materializer(system)

  private val log = OperatorLogger("skuber.operator.cache.SharedCache")

  // Cache instances by type key
  private val caches = TrieMap[String, InMemoryResourceCache[?]]()

  // Reflector instances by type key
  private val reflectors = TrieMap[String, Reflector[?]]()

  @volatile private var started = false

  def forResource[R <: ObjectResource](
    using rd: ResourceDefinition[R], fmt: Format[R]
  ): ResourceCache[R] =
    val key = cacheKey(rd)
    caches.getOrElseUpdate(key, {
      log.info(s"Creating cache for ${rd.spec.names.kind}")
      val cache = new InMemoryResourceCache[R]()

      // Create reflector but don't start it yet
      val reflector = new Reflector[R](client, cache, config, watchNamespace)
      reflectors.put(key, reflector)

      // If already started, start this reflector too
      if started then
        reflector.start()

      cache
    }).asInstanceOf[ResourceCache[R]]

  def start(): Future[Unit] =
    log.info("Starting shared cache")
    started = true

    // Start all reflectors
    val startFutures = reflectors.values.map(_.start()).toList
    Future.sequence(startFutures).map(_ => ())

  def stop(): Future[Unit] =
    log.info("Stopping shared cache")
    started = false

    // Stop all reflectors
    val stopFutures = reflectors.values.map(_.stop()).toList
    Future.sequence(stopFutures).map(_ => ())

  def waitForSync(timeout: FiniteDuration): Future[Boolean] =
    val deadline = timeout.fromNow

    def checkAll(): Future[Boolean] =
      if hasSynced then
        Future.successful(true)
      else if deadline.isOverdue then
        Future.successful(false)
      else
        // Poll every 100ms
        org.apache.pekko.pattern.after(100.millis) {
          checkAll()
        }

    checkAll()

  def hasSynced: Boolean =
    reflectors.values.forall(_.hasSynced)

  private def cacheKey(rd: ResourceDefinition[?]): String =
    s"${rd.spec.group.getOrElse("")}/${rd.spec.defaultVersion}/${rd.spec.names.kind}"
