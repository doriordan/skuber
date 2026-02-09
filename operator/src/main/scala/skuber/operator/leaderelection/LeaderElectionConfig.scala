package skuber.operator.leaderelection

import scala.concurrent.duration.*

/**
 * Configuration for leader election.
 */
case class LeaderElectionConfig(
  /**
   * Name of the lease/lock resource.
   * This is used to coordinate leader election between instances.
   */
  leaseName: String,

  /**
   * Namespace where the lease resource lives.
   */
  leaseNamespace: String,

  /**
   * Identity of this leader election participant.
   * Should be unique per instance (e.g., pod name).
   */
  identity: String,

  /**
   * Duration that non-leader candidates wait before forcing acquisition.
   * Should be greater than renewDeadline.
   */
  leaseDuration: FiniteDuration = 15.seconds,

  /**
   * Duration that the leader will retry refreshing leadership before giving up.
   */
  renewDeadline: FiniteDuration = 10.seconds,

  /**
   * Duration between retries when trying to acquire or renew the lease.
   */
  retryPeriod: FiniteDuration = 2.seconds
):
  require(leaseDuration > renewDeadline, "leaseDuration must be greater than renewDeadline")
  require(renewDeadline > retryPeriod, "renewDeadline must be greater than retryPeriod")

object LeaderElectionConfig:
  /**
   * Create a default configuration using the pod name from environment.
   */
  def withDefaults(leaseName: String, leaseNamespace: String): LeaderElectionConfig =
    val identity = sys.env.getOrElse("POD_NAME",
      sys.env.getOrElse("HOSTNAME",
        java.util.UUID.randomUUID().toString
      )
    )
    LeaderElectionConfig(leaseName, leaseNamespace, identity)
