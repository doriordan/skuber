package skuber.operator.cache

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Keep, RestartSource, Sink}
import org.apache.pekko.stream.{KillSwitch, KillSwitches, Materializer, RestartSettings}
import play.api.libs.json.Format
import skuber.api.client.{EventType, WatchEvent}
import skuber.json.format.ListResourceFormat
import skuber.model.{ListResource, ObjectResource, ResourceDefinition}
import skuber.pekkoclient.PekkoKubernetesClient
import skuber.operator.reconciler.OperatorLogger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/**
 * Configuration for the Reflector.
 */
case class ReflectorConfig(
  /** Minimum backoff before retrying after an error */
  minBackoff: FiniteDuration = 1.second,

  /** Maximum backoff before retrying after an error */
  maxBackoff: FiniteDuration = 30.seconds,

  /** Random factor for backoff jitter */
  randomFactor: Double = 0.2,

  /** Maximum restarts within a window before giving up */
  maxRestarts: Int = 20,

  /** Time window for counting restarts */
  maxRestartsWithin: FiniteDuration = 5.minutes,

  /** Buffer size for watch events */
  watchBufferSize: Int = 1024,

  /**
   * Use sendInitialEvents (streaming list) instead of List+Watch.
   *
   * When true, uses a single watch request with sendInitialEvents=true which:
   * - Streams all existing resources as ADDED events
   * - Sends a BOOKMARK event when initial sync is complete
   * - Continues with live events
   *
   * This eliminates the gap between List and Watch where events could be missed.
   * Requires Kubernetes 1.27+ (beta) or 1.28+ (GA).
   *
   * When false (default), uses traditional List+Watch pattern for compatibility
   * with older Kubernetes versions.
   */
  useStreamingList: Boolean = false
)

object ReflectorConfig:
  val default: ReflectorConfig = ReflectorConfig()

  /** Config optimized for Kubernetes 1.28+ with streaming list support */
  val streamingList: ReflectorConfig = ReflectorConfig(useStreamingList = true)

/**
 * Reflector watches a resource type and keeps the cache updated.
 * Implements the List+Watch pattern with automatic reconnection.
 *
 * The pattern is:
 * 1. List all resources to populate initial cache
 * 2. Watch for changes starting from the list's resourceVersion
 * 3. Apply changes to cache
 * 4. On error/disconnect, restart from step 1 or 2
 */
class Reflector[R <: ObjectResource](
  client: PekkoKubernetesClient,
  cache: InMemoryResourceCache[R],
  config: ReflectorConfig = ReflectorConfig.default,
  namespace: Option[String] = None  // None = watch all namespaces
)(using rd: ResourceDefinition[R], fmt: Format[R], system: ActorSystem):

  // Derive ListResource format from the item format
  private given listFmt: Format[ListResource[R]] = ListResourceFormat[R]

  given ExecutionContext = system.dispatcher
  given Materializer = Materializer(system)

  private val log = OperatorLogger(s"skuber.operator.cache.Reflector[${rd.spec.names.kind}]")

  @volatile private var killSwitch: Option[KillSwitch] = None
  @volatile private var lastResourceVersion: Option[String] = None
  private val startedPromise = Promise[Unit]()

  /**
   * Start the reflector.
   * Returns when initial list is complete and watch is running.
   */
  def start(): Future[Unit] =
    log.info(s"Starting reflector for ${rd.spec.names.kind}")
    runListAndWatch()
    startedPromise.future

  /**
   * Stop the reflector gracefully.
   */
  def stop(): Future[Unit] =
    log.info(s"Stopping reflector for ${rd.spec.names.kind}")
    killSwitch.foreach(_.shutdown())
    Future.successful(())

  /**
   * Check if initial sync is complete.
   */
  def hasSynced: Boolean = cache.hasSynced

  private def runListAndWatch(): Unit =
    if config.useStreamingList then
      runStreamingList()
    else
      runTraditionalListAndWatch()

  /**
   * Traditional List+Watch pattern:
   * 1. List all resources
   * 2. Watch from the list's resourceVersion
   */
  private def runTraditionalListAndWatch(): Unit =
    val restartSettings = RestartSettings(
      minBackoff = config.minBackoff,
      maxBackoff = config.maxBackoff,
      randomFactor = config.randomFactor
    ).withMaxRestarts(config.maxRestarts, config.maxRestartsWithin)

    val watchSource = RestartSource.withBackoff(restartSettings) { () =>
      // Each restart does a fresh list then watch
      val watcher = client.getWatcher[R]

      // First, list all resources
      val listFuture = namespace match
        case Some(ns) => client.usingNamespace(ns).list[ListResource[R]]()
        case None => client.listInCluster[ListResource[R]](None)

      org.apache.pekko.stream.scaladsl.Source.futureSource {
        listFuture.map { list =>
          log.info(s"Listed ${list.items.size} ${rd.spec.names.kind} resources")

          // Replace cache contents
          cache.replace(list.items)

          // Extract resource version for watch
          val rv = list.metadata.map(_.resourceVersion).getOrElse("")
          lastResourceVersion = Some(rv)

          // Mark as synced after first list
          if !cache.hasSynced then
            cache.markSynced()
            startedPromise.trySuccess(())

          // Start watching from this version
          log.debug(s"Starting watch from resourceVersion=$rv")
          watcher.watchStartingFromVersion(rv)
        }
      }.mapMaterializedValue(_ => org.apache.pekko.NotUsed)
    }

    val (ks, done) = watchSource
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.foreach(processEvent))(Keep.both)
      .run()

    killSwitch = Some(ks)

    // Log when watch stream completes
    done.onComplete {
      case Success(_) =>
        log.info(s"Watch stream completed for ${rd.spec.names.kind}")
      case Failure(e) =>
        log.error(s"Watch stream failed for ${rd.spec.names.kind}", e)
    }

  /**
   * Streaming list pattern (sendInitialEvents):
   * Single watch request that streams initial state as ADDED events,
   * then sends BOOKMARK when initial sync is complete, then continues
   * with live events.
   *
   * Requires Kubernetes 1.27+ (beta) or 1.28+ (GA).
   */
  private def runStreamingList(): Unit =
    val restartSettings = RestartSettings(
      minBackoff = config.minBackoff,
      maxBackoff = config.maxBackoff,
      randomFactor = config.randomFactor
    ).withMaxRestarts(config.maxRestarts, config.maxRestartsWithin)

    val watchSource = RestartSource.withBackoff(restartSettings) { () =>
      log.info(s"Starting streaming list for ${rd.spec.names.kind}")

      // Clear cache on restart - we'll rebuild from streamed events
      cache.replace(Nil)

      // Use watchWithInitialEvents for streaming list
      val eventSource = namespace match
        case Some(ns) =>
          // For namespaced watch, get watcher from namespace-scoped client
          // The implementation returns PekkoKubernetesClient, so this cast is safe
          val nsClient = client.usingNamespace(ns).asInstanceOf[PekkoKubernetesClient]
          nsClient.getWatcher[R].watchWithInitialEvents()
        case None =>
          client.getWatcher[R].watchClusterWithInitialEvents()

      eventSource
    }

    val (ks, done) = watchSource
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.foreach(processEvent))(Keep.both)
      .run()

    killSwitch = Some(ks)

    done.onComplete {
      case Success(_) =>
        log.info(s"Streaming list completed for ${rd.spec.names.kind}")
      case Failure(e) =>
        log.error(s"Streaming list failed for ${rd.spec.names.kind}", e)
    }

  /** Annotation key indicating initial events have ended */
  private val InitialEventsEndAnnotation = "k8s.io/initial-events-end"

  private def processEvent(event: WatchEvent[R]): Unit =
    // Update resource version tracking
    val rv = event._object.metadata.resourceVersion
    if rv.nonEmpty then
      lastResourceVersion = Some(rv)

    event._type match
      case EventType.ADDED =>
        log.debug(s"ADDED ${event._object.metadata.name}")
        cache.add(event._object)

      case EventType.MODIFIED =>
        log.debug(s"MODIFIED ${event._object.metadata.name}")
        cache.update(event._object)

      case EventType.DELETED =>
        log.debug(s"DELETED ${event._object.metadata.name}")
        cache.delete(event._object)

      case EventType.BOOKMARK =>
        // Check if this is the initial-events-end bookmark
        val isInitialEventsEnd = event._object.metadata.annotations
          .get(InitialEventsEndAnnotation)
          .contains("true")

        if isInitialEventsEnd then
          log.info(s"Initial events complete for ${rd.spec.names.kind}")
          if !cache.hasSynced then
            cache.markSynced()
            startedPromise.trySuccess(())
        else
          log.debug(s"BOOKMARK for ${rd.spec.names.kind} at rv=$rv")

      case EventType.ERROR =>
        log.warn(s"ERROR event for ${event._object.metadata.name}")
        // Error events typically indicate we need to re-list
        // The RestartSource will handle this
