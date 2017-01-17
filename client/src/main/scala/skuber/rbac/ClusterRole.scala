package skuber.rbac

import skuber.{ObjectMeta, ObjectResource}

/**
  * Created by jordan on 1/12/17.
  */
case class ClusterRole(
    kind: String = "ClusterRole",
    override val apiVersion: String = rbacAPIVersion,
    metadata: ObjectMeta = ObjectMeta(),
    rules: List[PolicyRule]
) extends ObjectResource {

}
