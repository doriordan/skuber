package skuber.operator.reconciler

import scala.concurrent.duration.FiniteDuration

/**
 * Result of a reconciliation attempt.
 *
 * The reconciler returns one of these to indicate what should happen next.
 */
sealed trait ReconcileResult

object ReconcileResult:
  /**
   * Reconciliation complete, no requeue needed.
   * The resource is in the desired state.
   */
  case object Done extends ReconcileResult

  /**
   * Requeue immediately.
   * Use when waiting for an external condition that may already be satisfied.
   */
  case class Requeue(reason: String = "") extends ReconcileResult

  /**
   * Requeue after the specified delay.
   * Use when waiting for an external condition that needs time.
   */
  case class RequeueAfter(delay: FiniteDuration, reason: String = "") extends ReconcileResult
