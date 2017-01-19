package skuber.rbac

import skuber.{ObjectMeta, ObjectResource}

/**
  * Created by jordan on 1/12/17.
  */
case class RoleBinding(
    kind: String = "RoleBinding",
    version: String = rbacAPIVersion,
    metadata: ObjectMeta,
    roleRef: RoleRef,
    subjects: List[Subject]
) extends ObjectResource {

}
