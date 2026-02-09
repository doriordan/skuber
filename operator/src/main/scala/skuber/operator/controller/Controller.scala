package skuber.operator.controller

import org.apache.pekko.stream.scaladsl.Source
import skuber.model.ObjectResource
import skuber.operator.reconciler.ReconcileOutcome

import scala.concurrent.Future

/**
 * A Controller manages reconciliation for a single resource type.
 * One controller per CRD following the kubebuilder pattern.
 *
 * The controller:
 * - Watches the primary resource type and related resources
 * - Deduplicates events into work items
 * - Calls the reconciler for each work item
 * - Handles requeue and error retry
 *
 * @tparam R The primary resource type this controller manages
 */
trait Controller[R <: ObjectResource]:

  /**
   * The kind of resource this controller manages.
   */
  def kind: String

  /**
   * Start the controller.
   * Begins watching resources and processing reconciliations.
   */
  def start(): Future[Unit]

  /**
   * Stop the controller gracefully.
   * Completes in-flight reconciliations before stopping.
   */
  def stop(): Future[Unit]

  /**
   * Stream of reconciliation outcomes for monitoring.
   */
  def outcomes: Source[ReconcileOutcome[R], ?]

  /**
   * Check if the controller is running.
   */
  def isRunning: Boolean
