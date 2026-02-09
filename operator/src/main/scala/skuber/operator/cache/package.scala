package skuber.operator

/**
 * Caching infrastructure for the operator framework.
 *
 * The cache uses the Reflector pattern from controller-runtime:
 * - [[Reflector]] performs List+Watch and updates the cache
 * - [[ResourceCache]] provides read access to cached resources
 * - [[SharedCache]] manages caches for multiple resource types
 *
 * Benefits:
 * - Reduces load on the Kubernetes API server
 * - Provides fast, local access to resources
 * - Shared across controllers watching the same types
 */
package object cache
