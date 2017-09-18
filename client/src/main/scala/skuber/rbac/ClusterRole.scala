package skuber.rbac

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition}

/**
  * Created by jordan on 1/12/17.
  */
case class ClusterRole(
    kind: String = "ClusterRole",
    override val apiVersion: String = rbacAPIVersion,
    metadata: ObjectMeta = ObjectMeta(),
    rules: List[PolicyRule]
) extends ObjectResource

object ClusterRole {

  implicit val crDef = new ResourceDefinition[ClusterRole] {
    def spec = NonCoreResourceSpecification (
      group=Some("rbac.authorization.k8s.io"),
      version="v1beta1",
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
