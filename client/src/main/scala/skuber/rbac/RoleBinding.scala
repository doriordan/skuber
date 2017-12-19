package skuber.rbac

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition}

/**
  * Created by jordan on 1/12/17.
  */
case class RoleBinding(
    kind: String = "RoleBinding",
    apiVersion: String = rbacAPIVersion,
    metadata: ObjectMeta,
    roleRef: RoleRef,
    subjects: List[Subject]
) extends ObjectResource

object RoleBinding {

  def specification = NonCoreResourceSpecification (
    group=Some("rbac.authorization.k8s.io"),
    version="v1beta1",
    scope = Scope.Namespaced,
    names=Names(
      plural = "rolebindings",
      singular = "rolebinding",
      kind = "RoleBinding",
      shortNames = Nil
    )
  )

  implicit val roleDef = new ResourceDefinition[RoleBinding] { def spec = specification }
  implicit val roleListDef = new ResourceDefinition[RoleBindingList] { def spec = specification }
}
