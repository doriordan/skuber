package skuber

/**
  * The skuber.apps package contains classes and methods for supporting the Kubernetes Apps Group API.
  * This currently (Kubernetes V1.7) includes StatefulSet and Deployment resource type.
  *
  * @author Hollin Wilkins
  */
package object apps {
  val appsAPIVersion = "apps/v1beta1"

  type StatefulSetList = ListResource[StatefulSet]
  type DeploymentList = ListResource[Deployment]

}
