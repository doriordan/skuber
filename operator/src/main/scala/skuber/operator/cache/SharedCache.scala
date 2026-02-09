package skuber.operator.cache

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import play.api.libs.json.Format
import skuber.model.{ObjectResource, ResourceDefinition}

/**
 * Shared cache across all controllers in a manager.
 * Uses the reflector pattern: List + Watch → local store.
 *
 * Benefits:
 * - Reduces API server load (reads from cache)
 * - Shared across controllers watching same types
 * - Provides consistent view within reconciliation
 */
trait SharedCache:

  /**
   * Get or create a cache for a specific resource type.
   * First call triggers List+Watch; subsequent calls return same cache.
   *
   * @tparam R The resource type
   * @return The resource cache for this type
   */
  def forResource[R <: ObjectResource](
    using rd: ResourceDefinition[R], fmt: Format[R]
  ): ResourceCache[R]

  /**
   * Start all reflectors and begin populating caches.
   * Called by ControllerManager.start().
   */
  def start(): Future[Unit]

  /**
   * Stop all reflectors gracefully.
   * Called by ControllerManager.stop().
   */
  def stop(): Future[Unit]

  /**
   * Wait for initial cache sync on all registered resource types.
   *
   * @param timeout Maximum time to wait
   * @return true if all caches synced, false if timeout
   */
  def waitForSync(timeout: FiniteDuration): Future[Boolean]

  /**
   * Check if all registered caches have synced.
   */
  def hasSynced: Boolean
