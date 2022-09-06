package skuber.rbac

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition, ResourceSpecification}

/**
  * Created by jordan on 1/12/17.
  */
case class RoleBinding(kind: String = "RoleBinding",
    apiVersion: String = rbacAPIVersion,
    metadata: ObjectMeta,
    roleRef: RoleRef,
    subjects: List[Subject]
) extends ObjectResource

object RoleBinding {

  def specification: NonCoreResourceSpecification = NonCoreResourceSpecification (apiGroup="rbac.authorization.k8s.io",
    version="v1beta1",
    scope = Scope.Namespaced,
    names=Names(plural = "rolebindings",
      singular = "rolebinding",
      kind = "RoleBinding",
      shortNames = Nil))

  implicit val roleDef: ResourceDefinition[RoleBinding] = new ResourceDefinition[RoleBinding] { def spec: ResourceSpecification = specification }
  implicit val roleListDef: ResourceDefinition[RoleBindingList] = new ResourceDefinition[RoleBindingList] { def spec: ResourceSpecification = specification }
}
