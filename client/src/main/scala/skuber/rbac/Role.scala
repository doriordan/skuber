package skuber.rbac

import skuber.{ObjectMeta, ObjectResource}

/**
  * Created by jordan on 1/12/17.
  */
case class Role(
    kind: String = "Role",
    version: String = rbacAPIVersion,
    metadata: ObjectMeta,
    rules: List[PolicyRule]
) extends ObjectResource {

}
