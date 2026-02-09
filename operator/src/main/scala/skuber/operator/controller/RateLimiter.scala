package skuber.operator.controller

import skuber.operator.reconciler.NamespacedName

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.*

/**
 * Configuration for rate limiting.
 */
case class RateLimiterConfig(
  /** Base delay for first retry */
  baseDelay: FiniteDuration = 5.millis,

  /** Maximum delay between retries */
  maxDelay: FiniteDuration = 16.minutes,

  /** Multiplier for exponential backoff */
  multiplier: Double = 2.0
)

object RateLimiterConfig:
  val default: RateLimiterConfig = RateLimiterConfig()

/**
 * Rate limiter using exponential backoff.
 * Tracks failure counts per key and calculates appropriate retry delays.
 */
class RateLimiter(config: RateLimiterConfig = RateLimiterConfig.default):

  // Failure counts per key
  private val failures = TrieMap[NamespacedName, Int]()

  /**
   * Calculate the delay for the next retry of this key.
   */
  def when(key: NamespacedName): FiniteDuration =
    val count = failures.getOrElseUpdate(key, 0)
    failures.put(key, count + 1)

    val delayMillis = (config.baseDelay.toMillis * math.pow(config.multiplier, count.toDouble)).toLong
    val cappedMillis = math.min(delayMillis, config.maxDelay.toMillis)
    cappedMillis.millis

  /**
   * Get current failure count for a key.
   */
  def numRequeues(key: NamespacedName): Int =
    failures.getOrElse(key, 0)

  /**
   * Reset the failure count for a key.
   * Call after successful reconciliation.
   */
  def forget(key: NamespacedName): Unit =
    failures.remove(key)
