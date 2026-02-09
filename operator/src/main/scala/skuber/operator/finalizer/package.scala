package skuber.operator

/**
 * Finalizer support for the operator framework.
 *
 * Finalizers allow cleanup logic to run before a resource is deleted.
 * The resource cannot be garbage collected until all finalizers are removed.
 *
 * Key components:
 * - [[FinalizerId]]: Unique identifier for a finalizer
 * - [[FinalizerSupport]]: Mixin trait for reconcilers
 * - [[FinalizerHandler]]: Helper for handling the finalizer lifecycle
 */
package object finalizer
