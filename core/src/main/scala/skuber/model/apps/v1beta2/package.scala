package skuber.model.apps

import skuber.model.ListResource

/**
  * @author David O'Riordan
  *
  * This package supports the "apps
  */
package object v1beta2 {
  val appsAPIVersion = "apps/v1beta2"

  type StatefulSetList = ListResource[StatefulSet]
  type DeploymentList = ListResource[Deployment]
}
