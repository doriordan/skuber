package skuber.apps

import skuber.ListResource

/**
  * @author David O'Riordan
  *
  * This package supports the "apps
  */
package object v1 {
  val appsAPIVersion = "apps/v1"

  type StatefulSetList = ListResource[skuber.apps.v1.StatefulSet]
  type DeploymentList = ListResource[skuber.apps.v1.Deployment]
  type ReplicaSetList = ListResource[skuber.apps.v1.ReplicaSet]
  type DaemonSetList = ListResource[skuber.apps.v1.DaemonSet]
}
