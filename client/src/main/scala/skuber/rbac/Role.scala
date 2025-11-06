package skuber.rbac

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition}

/**
  * Created by jordan on 1/12/17.
  */
case class Role(
    kind: String = "Role",
    apiVersion: String = rbacAPIVersion,
    metadata: ObjectMeta,
    rules: List[PolicyRule]
) extends ObjectResource

object Role {
  implicit val roleDef = new ResourceDefinition[Role] {
    def spec = NonCoreResourceSpecification (
      apiGroup="rbac.authorization.k8s.io",
      version="v1",
      scope = Scope.Namespaced,
      names=Names(
        plural = "roles",
        singular = "role",
        kind = "Role",
        shortNames = Nil
      )
    )
  }
}
