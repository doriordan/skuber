package skuber.model.authorization.rbac

import skuber.model.ResourceSpecification.{Names, Scope}
import skuber.model.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition}

/**
  * Created by jordan on 1/12/17.
  */
case class ClusterRole(
  kind: String = "ClusterRole",
  apiVersion: String = rbacAPIVersion,
  metadata: ObjectMeta = ObjectMeta(),
  rules: List[PolicyRule]
) extends ObjectResource

object ClusterRole {

  implicit val crDef: ResourceDefinition[ClusterRole] = new ResourceDefinition[ClusterRole] {
    def spec = NonCoreResourceSpecification (
      apiGroup="rbac.authorization.k8s.io",
      version="v1",
      scope = Scope.Cluster,
      names=Names(
        plural = "clusterroles",
        singular = "clusterrole",
        kind = "ClusterRole",
        shortNames = Nil
      )
    )
  }

}
