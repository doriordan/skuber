package skuber.operator.cache

import skuber.model.ObjectResource

/**
 * Events emitted by the resource cache when resources change.
 * These can be observed by controllers to trigger reconciliation.
 */
sealed trait CacheEvent[+R]

object CacheEvent:
  /**
   * A resource was added to the cache.
   */
  case class Added[R](resource: R) extends CacheEvent[R]

  /**
   * A resource was updated in the cache.
   * Provides both old and new versions for comparison.
   */
  case class Updated[R](oldResource: R, newResource: R) extends CacheEvent[R]

  /**
   * A resource was deleted from the cache.
   */
  case class Deleted[R](resource: R) extends CacheEvent[R]

  /**
   * Initial cache sync is complete.
   * The cache now has a consistent view from the API server.
   */
  case object Synced extends CacheEvent[Nothing]
