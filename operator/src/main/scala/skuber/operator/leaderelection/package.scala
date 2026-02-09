package skuber.operator

/**
 * Leader election for high-availability operators.
 *
 * When running multiple replicas of an operator, only one should be active
 * at a time. Leader election ensures this by using a distributed lock.
 *
 * This implementation uses ConfigMaps as the lock mechanism, which is
 * compatible with all Kubernetes versions. Future versions may use the
 * Lease resource for improved performance.
 *
 * Key components:
 * - [[LeaderElectionConfig]]: Configuration for leader election
 * - [[LeaderElection]]: The leader election implementation
 */
package object leaderelection
