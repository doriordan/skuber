package skuber.operator

/**
 * Core reconciler types for building Kubernetes operators.
 *
 * The reconciler pattern is the heart of the operator framework:
 * - A [[Reconciler]] contains business logic to ensure desired state
 * - [[ReconcileContext]] provides access to client, cache, and utilities
 * - [[ReconcileResult]] indicates whether to requeue
 */
package object reconciler
