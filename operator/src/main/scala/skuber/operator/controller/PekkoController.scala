package skuber.operator.controller

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Keep, Sink, Source}
import org.apache.pekko.stream.{KillSwitch, KillSwitches, Materializer}
import play.api.libs.json.Format
import skuber.api.client.EventType
import skuber.model.{ObjectResource, ResourceDefinition}
import skuber.operator.cache.{CacheEvent, ResourceCache}
import skuber.operator.event.EventRecorder
import skuber.operator.reconciler.*

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

/**
 * Pekko Streams-based controller implementation.
 */
class PekkoController[R <: ObjectResource](
  manager: ControllerManager,
  reconciler: Reconciler[R],
  owns: List[OwnedResource[?]],
  watches: List[WatchedResource[?]],
  concurrency: Int,
  workQueueConfig: WorkQueueConfig
)(using rd: ResourceDefinition[R], fmt: Format[R], system: ActorSystem) extends Controller[R]:

  given ExecutionContext = system.dispatcher
  given Materializer = Materializer(system)

  private val log = OperatorLogger(s"skuber.operator.controller.${rd.spec.names.kind}")

  private val workQueue = new WorkQueue(workQueueConfig)

  @volatile private var running = false
  @volatile private var killSwitch: Option[KillSwitch] = None

  // Outcome stream for monitoring
  private val (outcomeQueue, outcomeSource) = Source
    .queue[ReconcileOutcome[R]](bufferSize = 1024)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()

  def kind: String = rd.spec.names.kind

  def isRunning: Boolean = running

  def outcomes: Source[ReconcileOutcome[R], ?] = outcomeSource

  def start(): Future[Unit] =
    log.info(s"Starting controller for $kind")
    running = true

    // Get cache for primary resource
    val cache = manager.cache.forResource[R]

    // Subscribe to primary resource events
    setupPrimaryWatch(cache)

    // Subscribe to owned resource events
    owns.foreach(setupOwnedWatch)

    // Subscribe to watched resource events
    watches.foreach(setupRelatedWatch)

    // Start the reconcile loop
    startReconcileLoop(cache)

    Future.successful(())

  def stop(): Future[Unit] =
    log.info(s"Stopping controller for $kind")
    running = false
    workQueue.shutdown()
    killSwitch.foreach(_.shutdown())
    Future.successful(())

  private def setupPrimaryWatch(cache: ResourceCache[R]): Unit =
    cache.events.runForeach {
      case CacheEvent.Added(resource) =>
        enqueue(resource)
      case CacheEvent.Updated(_, resource) =>
        enqueue(resource)
      case CacheEvent.Deleted(resource) =>
        enqueue(resource)
      case CacheEvent.Synced =>
        log.info(s"Cache synced for $kind")
    }

  private def setupOwnedWatch[O <: ObjectResource](owned: OwnedResource[O]): Unit =
    given ResourceDefinition[O] = owned.rd
    given Format[O] = owned.fmt

    val cache = manager.cache.forResource[O]
    cache.events.runForeach {
      case CacheEvent.Added(resource) =>
        enqueueOwner(resource)
      case CacheEvent.Updated(_, resource) =>
        enqueueOwner(resource)
      case CacheEvent.Deleted(resource) =>
        enqueueOwner(resource)
      case CacheEvent.Synced =>
        ()
    }

  private def setupRelatedWatch[O <: ObjectResource](watched: WatchedResource[O]): Unit =
    given ResourceDefinition[O] = watched.rd
    given Format[O] = watched.fmt

    val cache = manager.cache.forResource[O]
    cache.events.runForeach {
      case CacheEvent.Added(resource) =>
        watched.mapper(resource).foreach(workQueue.add)
      case CacheEvent.Updated(_, resource) =>
        watched.mapper(resource).foreach(workQueue.add)
      case CacheEvent.Deleted(resource) =>
        watched.mapper(resource).foreach(workQueue.add)
      case CacheEvent.Synced =>
        ()
    }

  private def enqueue(resource: R): Unit =
    workQueue.add(NamespacedName(resource))

  private def enqueueOwner[O <: ObjectResource](resource: O): Unit =
    // Find owner references that match our primary type
    resource.metadata.ownerReferences
      .filter(ref => ref.kind == kind && ref.apiVersion == s"${rd.spec.group.getOrElse("")}/${rd.spec.defaultVersion}")
      .foreach { ref =>
        workQueue.add(NamespacedName(resource.metadata.namespace, ref.name))
      }

  private def startReconcileLoop(cache: ResourceCache[R]): Unit =
    val (ks, done) = workQueue.source
      .mapAsync(concurrency) { key =>
        reconcileKey(key, cache)
      }
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.foreach { outcome =>
        outcomeQueue.offer(outcome)
        handleOutcome(outcome)
      })(Keep.both)
      .run()

    killSwitch = Some(ks)

    done.onComplete {
      case Success(_) =>
        log.info(s"Reconcile loop completed for $kind")
      case Failure(e) =>
        log.error(s"Reconcile loop failed for $kind", e)
    }

  private def reconcileKey(key: NamespacedName, cache: ResourceCache[R]): Future[ReconcileOutcome[R]] =
    val startTime = System.nanoTime()

    cache.get(key) match
      case None =>
        // Resource was deleted
        workQueue.done(key)
        Future.successful(ReconcileOutcome(
          key = key,
          resource = None,
          result = Right(ReconcileResult.Done),
          duration = (System.nanoTime() - startTime).nanos
        ))

      case Some(resource) =>
        val ctx = createContext(resource, cache)

        reconciler.reconcile(resource, ctx)
          .map { result =>
            ReconcileOutcome(
              key = key,
              resource = Some(resource),
              result = Right(result),
              duration = (System.nanoTime() - startTime).nanos
            )
          }
          .recover { case e =>
            log.error(s"Reconcile error for $key", e)
            ReconcileOutcome(
              key = key,
              resource = Some(resource),
              result = Left(e),
              duration = (System.nanoTime() - startTime).nanos
            )
          }

  private def handleOutcome(outcome: ReconcileOutcome[R]): Unit =
    outcome.result match
      case Right(ReconcileResult.Done) =>
        workQueue.forget(outcome.key)

      case Right(ReconcileResult.Requeue(reason)) =>
        if reason.nonEmpty then log.debug(s"Requeue ${outcome.key}: $reason")
        workQueue.done(outcome.key)
        workQueue.add(outcome.key)

      case Right(ReconcileResult.RequeueAfter(delay, reason)) =>
        if reason.nonEmpty then log.debug(s"Requeue ${outcome.key} after $delay: $reason")
        workQueue.done(outcome.key)
        workQueue.addAfter(outcome.key, delay)

      case Left(_) =>
        // Error - use rate-limited requeue
        workQueue.done(outcome.key)
        workQueue.addRateLimited(outcome.key)

  private def createContext(resource: R, cache: ResourceCache[R]): ReconcileContext[R] =
    new PekkoReconcileContext[R](
      resource = resource,
      client = manager.client,
      sharedCache = manager.cache,
      eventRecorder = new EventRecorder(manager.client, resource),
      rd = rd
    )

/**
 * Pekko-based implementation of ReconcileContext.
 */
private class PekkoReconcileContext[R <: ObjectResource](
  resource: R,
  val client: skuber.pekkoclient.PekkoKubernetesClient,
  sharedCache: skuber.operator.cache.SharedCache,
  val eventRecorder: EventRecorder,
  rd: ResourceDefinition[R]
)(using system: ActorSystem) extends ReconcileContext[R]:

  given ExecutionContext = system.dispatcher

  val cache: skuber.operator.cache.SharedCache = sharedCache

  val log: OperatorLogger = OperatorLogger.forResource(
    rd.spec.names.kind,
    resource.metadata.namespace,
    resource.metadata.name
  )

  val resourceDefinition: ResourceDefinition[R] = rd

  def getRelated[O <: ObjectResource](name: String)(
    using ord: ResourceDefinition[O], fmt: Format[O]
  ): Future[Option[O]] =
    getRelatedInNamespace(name, resource.metadata.namespace)

  def getRelatedInNamespace[O <: ObjectResource](name: String, namespace: String)(
    using ord: ResourceDefinition[O], fmt: Format[O]
  ): Future[Option[O]] =
    // Try cache first
    val cached = cache.forResource[O].get(NamespacedName(namespace, name))
    if cached.isDefined then
      Future.successful(cached)
    else
      // Fall back to API
      client.usingNamespace(namespace).getOption[O](name)
