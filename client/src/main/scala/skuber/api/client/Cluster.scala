package skuber.api.client

/**
  * @author David O'Riordan
  *
  * Defines the details needed to communicate with the API server for a Kubernetes cluster
  */
case class Cluster(
  apiVersion: String = "v1",
  server: String = defaultApiServerURL,
  insecureSkipTLSVerify: Boolean = false,
  certificateAuthority: Option[PathOrData] = None
)
