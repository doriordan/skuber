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
import java.util.concurrent.locks.ReentrantLock

/**
 * Work queue that deduplicates items and handles rate limiting.
 *
 * Features:
 * - Deduplication: multiple events for same key become one work item
 * - FIFO ordering: items are processed in the order they were added
 * - Rate limiting: controls how fast items can be processed
 * - Delayed requeue: supports RequeueAfter with specific delays
 *
 * Implementation uses both a Set (for O(1) deduplication) and a Queue (for FIFO ordering).
 */
class WorkQueue(
  config: WorkQueueConfig = WorkQueueConfig.default
)(using system: ActorSystem):

  given ExecutionContext = system.dispatcher
  given Materializer = Materializer(system)

  // Lock for coordinating access to pending set and queue
  private val pendingLock = new ReentrantLock()

  // Items currently being processed
  private val processing = TrieMap[NamespacedName, Unit]()

  // Items that are "dirty" - need processing. This is the authoritative set of items
  // that need work. Items are added here first, then moved to the queue.
  private val dirty = TrieMap[NamespacedName, Unit]()

  // Items waiting to be processed - Queue for FIFO ordering
  // Items in this queue are always also in the dirty set
  private val pendingQueue = new ConcurrentLinkedQueue[NamespacedName]()

  // Delayed items (requeue after) - Map from key to scheduled process time
  private val delayed = TrieMap[NamespacedName, Instant]()

  // Rate limiting state per key
  private val rateLimiter = new RateLimiter(config.rateLimiterConfig)

  /**
   * Add an item to the queue.
   *
   * If already marked dirty, this is a no-op (deduplication).
   * If currently processing, marks as dirty so it will be re-queued when done.
   * Items are processed in FIFO order.
   */
  def add(key: NamespacedName): Unit =
    pendingLock.lock()
    try
      // If already dirty, nothing to do (deduplication)
      if dirty.contains(key) then
        return

      // Mark as dirty - this item needs processing
      dirty.put(key, ())

      // If currently processing, don't add to queue yet.
      // It will be re-queued when done() is called.
      if processing.contains(key) then
        return

      // Add to queue for processing
      pendingQueue.offer(key)
    finally
      pendingLock.unlock()

  /**
   * Add an item to be processed after a delay.
   * If already delayed with an earlier time, keeps the earlier time.
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
   *
   * If the item was marked dirty while processing (new events arrived),
   * it will be automatically re-queued for another reconciliation.
   */
  def done(key: NamespacedName): Unit =
    pendingLock.lock()
    try
      processing.remove(key)

      // If item was marked dirty while we were processing, re-queue it
      if dirty.contains(key) then
        pendingQueue.offer(key)
    finally
      pendingLock.unlock()

  /**
   * Mark an item as successfully processed (resets rate limiter).
   * Unlike done(), this also clears the dirty flag, preventing re-queue
   * even if new events arrived during processing.
   *
   * Use this when reconciliation succeeded and you've observed the latest state.
   * Use done() when reconciliation failed and you want events that arrived
   * during processing to trigger a re-reconciliation.
   */
  def forget(key: NamespacedName): Unit =
    pendingLock.lock()
    try
      rateLimiter.forget(key)
      dirty.remove(key)
      processing.remove(key)
    finally
      pendingLock.unlock()

  /**
   * Get the next item to process, if any.
   * Returns items in FIFO order.
   */
  def get(): Option[NamespacedName] =
    // First, move any ready delayed items to pending
    val now = Instant.now()
    delayed.foreach { case (key, processAt) =>
      if !processAt.isAfter(now) then
        delayed.remove(key)
        add(key)  // Use add() to maintain deduplication
    }

    // Get next pending item in FIFO order
    pendingLock.lock()
    try
      var result: Option[NamespacedName] = None
      var found = false

      while !found && !pendingQueue.isEmpty do
        val key = pendingQueue.poll()
        if key != null then
          // Check if still dirty (might have been removed by forget())
          if dirty.contains(key) then
            // Check not already processing (could happen with delayed items)
            if !processing.contains(key) then
              // Remove from dirty - we're about to process it
              dirty.remove(key)
              processing.put(key, ())
              result = Some(key)
              found = true
            // else: already processing, the dirty flag will cause re-queue in done()

      result
    finally
      pendingLock.unlock()

  /**
   * Create a stream of work items from this queue.
   */
  def source: Source[NamespacedName, ?] =
    Source.tick(0.millis, config.pollInterval, ())
      .mapConcat(_ => get().toList)

  /**
   * Number of items marked dirty (needing processing).
   */
  def len: Int = dirty.size

  /**
   * Number of items currently being processed.
   */
  def processingCount: Int = processing.size

  /**
   * Number of delayed items waiting.
   */
  def delayedCount: Int = delayed.size

  /**
   * Number of items in the pending queue.
   * Note: This may differ from len() as the queue may contain
   * stale entries for items no longer dirty.
   */
  def queueLen: Int = pendingQueue.size

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
