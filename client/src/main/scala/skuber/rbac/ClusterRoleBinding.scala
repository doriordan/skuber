package skuber.rbac

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition, ResourceSpecification}

/**
  * Created by jordan on 1/12/17.
  */
case class ClusterRoleBinding(kind: String = "ClusterRoleBinding",
    apiVersion: String = rbacAPIVersion,
    metadata: ObjectMeta,
    roleRef: Option[RoleRef],
    subjects: List[Subject]) extends ObjectResource

object ClusterRoleBinding {

  implicit val crDef: ResourceDefinition[ClusterRoleBinding] = new ResourceDefinition[ClusterRoleBinding] {
    def spec: ResourceSpecification = NonCoreResourceSpecification (apiGroup="rbac.authorization.k8s.io",
      version="v1beta1",
      scope = Scope.Cluster,
      names=Names(plural = "clusterrolebindings",
        singular = "clusterrolebinding",
        kind = "ClusterRoleBinding",
        shortNames = Nil))
  }
}