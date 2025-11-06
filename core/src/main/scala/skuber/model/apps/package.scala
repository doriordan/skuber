package skuber.model

/**
  * The skuber.apps package contains classes and methods for supporting the Kubernetes Apps Group API.
  * Subpackages contain Kubernetes group version specific definitions - except for legacy users v1
  * should be used.
  *
  * @author Hollin Wilkins
  */
package object apps {

  type StatefulSet = v1.StatefulSet
  type Deployment = v1.Deployment

  type StatefulSetList = ListResource[v1.StatefulSet]
  type DeploymentList = ListResource[v1.Deployment]
}
