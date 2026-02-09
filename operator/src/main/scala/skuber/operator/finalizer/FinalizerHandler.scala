package skuber.operator.finalizer

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Format
import skuber.model.{ObjectEditor, ObjectResource, ResourceDefinition}
import skuber.operator.reconciler.{ReconcileContext, Reconciler}

/**
 * Result of finalizer handling.
 */
sealed trait FinalizerAction[+R]

object FinalizerAction:
  /**
   * Continue with normal reconciliation.
   * The resource may have been updated with our finalizer.
   */
  case class Continue[R](resource: R) extends FinalizerAction[R]

  /**
   * Resource was cleaned up and finalizer was removed.
   * Deletion will proceed.
   */
  case class DeletedAndCleaned[R]() extends FinalizerAction[R]

  /**
   * Cleanup is still in progress.
   * The reconciler should requeue.
   */
  case class CleanupPending[R]() extends FinalizerAction[R]

  /**
   * Resource is being deleted but doesn't have our finalizer.
   * Nothing for us to do.
   */
  case class NotOurs[R]() extends FinalizerAction[R]

/**
 * Helper for handling the finalizer lifecycle in reconcile().
 */
object FinalizerHandler:

  /**
   * Standard finalizer handling pattern.
   *
   * Call this at the start of reconcile() to handle the finalizer lifecycle:
   * - If resource is being deleted and has our finalizer: run cleanup
   * - If resource is being deleted without our finalizer: nothing to do
   * - If resource is not being deleted: ensure finalizer is present, continue
   *
   * @param resource The resource being reconciled
   * @param ctx The reconcile context
   * @param support The reconciler with finalizer support
   * @return Action indicating what to do next
   */
  def handle[R <: ObjectResource](
    resource: R,
    ctx: ReconcileContext[R],
    support: FinalizerSupport[R] & Reconciler[R]
  )(using fmt: Format[R], rd: ResourceDefinition[R], editor: ObjectEditor[R], ec: ExecutionContext): Future[FinalizerAction[R]] =

    if support.isBeingDeleted(resource) then
      if support.hasFinalizer(resource) then
        // Resource is being deleted and has our finalizer - run cleanup
        support.finalize(resource, ctx).flatMap {
          case true =>
            // Cleanup succeeded, remove finalizer to allow deletion
            support.removeFinalizer(resource, ctx)
              .map(_ => FinalizerAction.DeletedAndCleaned())
          case false =>
            // Cleanup not complete, will be retried
            Future.successful(FinalizerAction.CleanupPending())
        }
      else
        // Being deleted but not our finalizer - nothing to do
        Future.successful(FinalizerAction.NotOurs())
    else
      // Not being deleted - ensure finalizer is present and continue
      support.ensureFinalizer(resource, ctx)
        .map(r => FinalizerAction.Continue(r))
