package skuber.apps

import skuber.ListResource

/**
  * @author David O'Riordan
  *
  * This package supports the "apps
  */
package object v1beta2 {
  val appsAPIVersion = "apps/v1beta2"

  type StatefulSetList = ListResource[skuber.apps.v1beta2.StatefulSet]
  type DeploymentList = ListResource[skuber.apps.v1beta2.Deployment]
}
