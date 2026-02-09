package skuber.operator.reconciler

import scala.concurrent.duration.FiniteDuration
import skuber.model.ObjectResource

/**
 * Outcome of a reconcile call, including metadata about the execution.
 *
 * @tparam R The resource type that was reconciled
 * @param key The namespaced name of the resource
 * @param resource The resource if it still exists (None if deleted)
 * @param result Either an error or the reconcile result
 * @param duration How long the reconciliation took
 */
case class ReconcileOutcome[R <: ObjectResource](
  key: NamespacedName,
  resource: Option[R],
  result: Either[Throwable, ReconcileResult],
  duration: FiniteDuration
):
  def isSuccess: Boolean = result.isRight
  def isError: Boolean = result.isLeft

  def error: Option[Throwable] = result.left.toOption

  def needsRequeue: Boolean = result match
    case Right(ReconcileResult.Done) => false
    case Right(_: ReconcileResult.Requeue) => true
    case Right(_: ReconcileResult.RequeueAfter) => true
    case Left(_) => true // Errors trigger requeue with backoff
