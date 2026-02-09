package skuber.operator.controller

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Flow, Source, SourceQueueWithComplete}
import org.apache.pekko.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import skuber.operator.reconciler.NamespacedName

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Work queue that deduplicates items and handles rate limiting.
 *
 * Features:
 * - Deduplication: multiple events for same key become one work item
 * - Rate limiting: controls how fast items can be processed
 * - Delayed requeue: supports RequeueAfter with specific delays
 */
class WorkQueue(
  config: WorkQueueConfig = WorkQueueConfig.default
)(using system: ActorSystem):

  given ExecutionContext = system.dispatcher
  given Materializer = Materializer(system)

  // Items currently being processed (for deduplication)
  private val processing = TrieMap[NamespacedName, Unit]()

  // Items waiting to be processed
  private val pending = TrieMap[NamespacedName, Instant]()

  // Delayed items (requeue after)
  private val delayed = TrieMap[NamespacedName, Instant]()

  // Rate limiting state per key
  private val rateLimiter = new RateLimiter(config.rateLimiterConfig)

  /**
   * Add an item to the queue.
   * If already pending or processing, this is a no-op (deduplication).
   */
  def add(key: NamespacedName): Unit =
    if !processing.contains(key) && !pending.contains(key) then
      pending.put(key, Instant.now())

  /**
   * Add an item to be processed after a delay.
   */
  def addAfter(key: NamespacedName, delay: FiniteDuration): Unit =
    val processAt = Instant.now().plusMillis(delay.toMillis)
    delayed.updateWith(key) {
      case Some(existing) if existing.isBefore(processAt) => Some(existing)
      case _ => Some(processAt)
    }

  /**
   * Add an item for rate-limited requeue.
   * Uses exponential backoff based on failure count.
   */
  def addRateLimited(key: NamespacedName): Unit =
    val delay = rateLimiter.when(key)
    addAfter(key, delay)

  /**
   * Mark an item as done processing.
   * Call this after reconciliation completes.
   */
  def done(key: NamespacedName): Unit =
    processing.remove(key)

  /**
   * Mark an item as successfully processed (resets rate limiter).
   */
  def forget(key: NamespacedName): Unit =
    rateLimiter.forget(key)
    done(key)

  /**
   * Get the next item to process, if any.
   */
  def get(): Option[NamespacedName] =
    // First, move any ready delayed items to pending
    val now = Instant.now()
    delayed.foreach { case (key, processAt) =>
      if !processAt.isAfter(now) then
        delayed.remove(key)
        pending.put(key, now)
    }

    // Get next pending item
    pending.keys.headOption.flatMap { key =>
      pending.remove(key)
      processing.put(key, ())
      Some(key)
    }

  /**
   * Create a stream of work items from this queue.
   */
  def source: Source[NamespacedName, ?] =
    Source.tick(0.millis, config.pollInterval, ())
      .mapConcat(_ => get().toList)

  /**
   * Length of the pending queue.
   */
  def len: Int = pending.size

  /**
   * Check if queue is shutting down.
   */
  @volatile private var shuttingDown = false

  def shutdown(): Unit =
    shuttingDown = true

  def isShuttingDown: Boolean = shuttingDown

/**
 * Configuration for the work queue.
 */
case class WorkQueueConfig(
  pollInterval: FiniteDuration = 50.millis,
  rateLimiterConfig: RateLimiterConfig = RateLimiterConfig.default
)

object WorkQueueConfig:
  val default: WorkQueueConfig = WorkQueueConfig()
