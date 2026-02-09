package skuber.operator.finalizer

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Format
import skuber.model.{ObjectEditor, ObjectMeta, ObjectResource, ResourceDefinition}
import skuber.operator.reconciler.{ReconcileContext, Reconciler}

/**
 * Mixin trait for reconcilers that need finalizer support.
 *
 * Finalizers allow cleanup logic to run before a resource is deleted.
 * The resource cannot be garbage collected until all finalizers are removed.
 *
 * Usage:
 * {{{
 * class MyReconciler extends Reconciler[MyResource] with FinalizerSupport[MyResource]:
 *   val finalizerId = FinalizerId("mycompany.com", "my-cleanup")
 *
 *   def reconcile(resource: MyResource, ctx: ReconcileContext[MyResource]) =
 *     FinalizerHandler.handle(resource, ctx, this).flatMap {
 *       case FinalizerAction.Continue(r) => doReconcile(r, ctx)
 *       case FinalizerAction.DeletedAndCleaned() => Future.successful(ReconcileResult.Done)
 *       case FinalizerAction.CleanupPending() => Future.successful(ReconcileResult.RequeueAfter(5.seconds))
 *       case FinalizerAction.NotOurs() => Future.successful(ReconcileResult.Done)
 *     }
 *
 *   override def finalize(resource: MyResource, ctx: ReconcileContext[MyResource]) =
 *     // Cleanup logic here
 *     Future.successful(true)
 * }}}
 */
trait FinalizerSupport[R <: ObjectResource]:
  self: Reconciler[R] =>

  /**
   * The finalizer ID for this controller.
   * Must be unique across all controllers managing this resource type.
   */
  def finalizerId: FinalizerId

  /**
   * Check if the resource has our finalizer.
   */
  def hasFinalizer(resource: R): Boolean =
    resource.metadata.finalizers.exists(_.contains(finalizerId.value))

  /**
   * Check if the resource is being deleted.
   * A resource is being deleted when it has a deletionTimestamp.
   */
  def isBeingDeleted(resource: R): Boolean =
    resource.metadata.deletionTimestamp.isDefined

  /**
   * Add our finalizer to the resource if not already present.
   * Should be called early in reconcile() for resources we manage.
   *
   * @return The updated resource (may be unchanged if finalizer was present)
   */
  def ensureFinalizer(resource: R, ctx: ReconcileContext[R])(
    using fmt: Format[R], rd: ResourceDefinition[R], editor: ObjectEditor[R], ec: ExecutionContext
  ): Future[R] =
    if hasFinalizer(resource) then
      Future.successful(resource)
    else
      val currentFinalizers = resource.metadata.finalizers.getOrElse(Nil)
      val updatedMeta = resource.metadata.copy(
        finalizers = Some(currentFinalizers :+ finalizerId.value)
      )
      val updatedResource = editor.updateMetadata(resource, updatedMeta)
      ctx.client.update(updatedResource)

  /**
   * Remove our finalizer from the resource.
   * Called after finalize() returns true.
   *
   * @return The updated resource
   */
  def removeFinalizer(resource: R, ctx: ReconcileContext[R])(
    using fmt: Format[R], rd: ResourceDefinition[R], editor: ObjectEditor[R], ec: ExecutionContext
  ): Future[R] =
    val currentFinalizers = resource.metadata.finalizers.getOrElse(Nil)
    val updatedMeta = resource.metadata.copy(
      finalizers = Some(currentFinalizers.filterNot(_ == finalizerId.value))
    )
    val updatedResource = editor.updateMetadata(resource, updatedMeta)
    ctx.client.update(updatedResource)
