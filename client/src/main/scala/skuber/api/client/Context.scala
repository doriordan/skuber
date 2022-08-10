package skuber.api.client

import skuber.Namespace

/**
  * @author David O'Riordan
  * Define the Kubernetes API context for requests
  */
case class Context(cluster: Cluster = Cluster(),
  authInfo: AuthInfo = NoAuth,
  namespace: Namespace = Namespace.default
)
