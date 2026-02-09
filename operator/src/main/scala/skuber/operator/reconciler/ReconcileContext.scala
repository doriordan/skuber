package skuber.operator.reconciler

import scala.concurrent.Future
import play.api.libs.json.Format
import skuber.model.{ObjectResource, ResourceDefinition}
import skuber.pekkoclient.PekkoKubernetesClient
import skuber.operator.cache.SharedCache
import skuber.operator.event.EventRecorder

/**
 * Context passed to the reconciler providing access to cluster and utilities.
 *
 * @tparam R The primary resource type being reconciled
 */
trait ReconcileContext[R <: ObjectResource]:

  /**
   * The Kubernetes client for API operations.
   * Use for creating, updating, and deleting resources.
   */
  def client: PekkoKubernetesClient

  /**
   * Shared cache for efficient reads.
   * Reads from cache are faster and reduce API server load.
   */
  def cache: SharedCache

  /**
   * Event recorder for creating Kubernetes Events.
   * Use to record significant occurrences during reconciliation.
   */
  def eventRecorder: EventRecorder

  /**
   * Structured logger with resource context automatically added.
   */
  def log: OperatorLogger

  /**
   * The resource definition for the primary resource type.
   */
  def resourceDefinition: ResourceDefinition[R]

  /**
   * Get a related resource from cache.
   * Falls back to API if not in cache.
   *
   * @param name Resource name (uses the same namespace as the primary resource)
   */
  def getRelated[O <: ObjectResource](name: String)(
    using rd: ResourceDefinition[O], fmt: Format[O]
  ): Future[Option[O]]

  /**
   * Get a related resource from cache in a specific namespace.
   */
  def getRelatedInNamespace[O <: ObjectResource](name: String, namespace: String)(
    using rd: ResourceDefinition[O], fmt: Format[O]
  ): Future[Option[O]]
