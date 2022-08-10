package skuber.rbac

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition, ResourceSpecification}

/**
 * Created by jordan on 1/12/17.
 */
case class Role(kind: String = "Role",
                apiVersion: String = rbacAPIVersion,
                metadata: ObjectMeta,
                rules: List[PolicyRule]) extends ObjectResource

object Role {
  implicit val roleDef: ResourceDefinition[Role] = new ResourceDefinition[Role] {
    def spec: ResourceSpecification = NonCoreResourceSpecification(apiGroup = "rbac.authorization.k8s.io",
      version = "v1beta1",
      scope = Scope.Namespaced,
      names = Names(plural = "roles",
        singular = "role",
        kind = "Role",
        shortNames = Nil))
  }
}
