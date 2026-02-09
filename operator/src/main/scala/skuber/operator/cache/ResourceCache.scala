package skuber.operator.cache

import org.apache.pekko.stream.scaladsl.Source
import skuber.model.{LabelSelector, ObjectResource}
import skuber.operator.reconciler.NamespacedName

/**
 * Cache for a specific resource type.
 * Provides efficient local access to resources without hitting the API server.
 *
 * @tparam R The resource type stored in this cache
 */
trait ResourceCache[R <: ObjectResource]:

  /**
   * Get a resource by name in the default namespace.
   */
  def get(name: String): Option[R]

  /**
   * Get a resource by namespaced name.
   */
  def get(key: NamespacedName): Option[R]

  /**
   * List all cached resources.
   */
  def list(): List[R]

  /**
   * List resources matching a label selector.
   */
  def list(selector: LabelSelector): List[R]

  /**
   * List resources in a specific namespace.
   */
  def listInNamespace(namespace: String): List[R]

  /**
   * Check if the initial sync is complete.
   * Before sync, the cache may be missing resources.
   */
  def hasSynced: Boolean

  /**
   * Stream of cache mutation events.
   * Controllers can subscribe to trigger reconciliation.
   */
  def events: Source[CacheEvent[R], ?]

  /**
   * Add a secondary index for efficient lookups.
   * The indexer function extracts index values from a resource.
   *
   * @param name Unique name for this index
   * @param indexer Function that returns index values for a resource
   */
  def addIndex(name: String, indexer: R => List[String]): Unit

  /**
   * Query resources by a secondary index.
   *
   * @param indexName The index name (must have been added with addIndex)
   * @param value The index value to query
   * @return Resources matching the index value
   */
  def byIndex(indexName: String, value: String): List[R]

  /**
   * Get all resources owned by a specific resource.
   * Uses the owner reference field selector.
   */
  def getOwnedBy(ownerUid: String): List[R]
