package skuber

import skuber.api.client._
import skuber.json.rbac.format._

/**
  * Created by jordan on 1/13/17.
  */
package object rbac {
  val rbacAPIVersion = "rbac.authorization.k8s.io/v1beta1"

  type ClusterRoleList = ListResource[ClusterRole]
  type ClusterRoleBindingList = ListResource[ClusterRoleBinding]
  type RoleList = ListResource[Role]
  type RoleBindingList = ListResource[RoleBinding]
}
