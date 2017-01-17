package skuber.rbac

import skuber.{ObjectMeta, ObjectResource}

/**
  * Created by jordan on 1/12/17.
  */
case class ClusterRoleBinding(
    kind: String = "ClusterRoleBinding",
    version: String = rbacAPIVersion,
    metadata: ObjectMeta,
    roleRef: Option[RoleRef],
    subjects: List[Subject]
) extends ObjectResource {

}
