package skuber.api.client

import com.amazonaws.regions.Regions

/**
 * @author David O'Riordan
 *
 *         Defines the details needed to communicate with the API server for a Kubernetes cluster
 */
case class Cluster(apiVersion: String = "v1",
                    server: String = defaultApiServerURL,
                    insecureSkipTLSVerify: Boolean = false,
                    certificateAuthority: Option[PathOrData] = None,
                    clusterName: Option[String] = None,
                    awsRegion: Option[Regions] = None) {
  def withName(name: String): Cluster = this.copy(clusterName = Some(name))

  def withAwsRegion(region: Regions): Cluster = this.copy(awsRegion = Some(region))
}
