package skuber.operator.reconciler

import scala.concurrent.Future
import skuber.model.ObjectResource

/**
 * The reconciler contains user business logic for a controller.
 *
 * Design principles (following kubebuilder/controller-runtime):
 * - Must be idempotent: same input should produce same output
 * - Should not assume why reconciliation was triggered
 * - Should reconstitute status from world state, not cached status
 *
 * @tparam R The custom resource type being reconciled
 */
trait Reconciler[R <: ObjectResource]:

  /**
   * Reconcile the resource to match desired state.
   *
   * This is called when:
   * - The resource is created, updated, or deleted
   * - An owned resource changes
   * - A watched related resource changes
   * - A requeue is triggered
   * - A periodic resync occurs
   *
   * The reconciler should NOT assume which of these triggered reconciliation.
   * It should always compare desired state (spec) with actual state and make corrections.
   *
   * @param resource Current state of the resource (from cache, may be slightly stale)
   * @param ctx Context providing client, cache, and utilities
   * @return Future containing the reconcile result
   */
  def reconcile(resource: R, ctx: ReconcileContext[R]): Future[ReconcileResult]

  /**
   * Called when a resource is being deleted and has this controller's finalizer.
   * Override to implement cleanup logic.
   *
   * Default implementation does nothing and allows deletion to proceed.
   *
   * @param resource The resource being deleted
   * @param ctx Reconcile context
   * @return Future[true] to remove finalizer and allow deletion,
   *         Future[false] to keep finalizer (block deletion until cleanup completes)
   */
  def finalize(resource: R, ctx: ReconcileContext[R]): Future[Boolean] =
    Future.successful(true)
