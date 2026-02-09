package skuber.operator

/**
 * Event recording for the operator framework.
 *
 * Kubernetes events communicate significant occurrences to users.
 * They appear in `kubectl describe` output and `kubectl get events`.
 *
 * Key components:
 * - [[EventRecorder]]: Records events for a resource
 * - [[EventType]]: Standard event types (Normal, Warning)
 */
package object event
