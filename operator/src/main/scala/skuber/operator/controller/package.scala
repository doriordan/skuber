package skuber.operator

/**
 * Controller infrastructure for the operator framework.
 *
 * The controller pattern follows kubebuilder/controller-runtime:
 * - One [[Controller]] per CRD (resource kind)
 * - [[ControllerManager]] orchestrates multiple controllers
 * - [[ControllerBuilder]] provides fluent API for construction
 *
 * Controllers watch resources, deduplicate events, and call reconcilers.
 */
package object controller
