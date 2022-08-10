package skuber.rbac

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition, ResourceSpecification}

/**
 * Created by jordan on 1/12/17.
 */
case class ClusterRole(kind: String = "ClusterRole",
                       override val apiVersion: String = rbacAPIVersion,
                       metadata: ObjectMeta = ObjectMeta(),
                       rules: List[PolicyRule]) extends ObjectResource

object ClusterRole {

  implicit val crDef: ResourceDefinition[ClusterRole] = new ResourceDefinition[ClusterRole] {
    def spec: ResourceSpecification = NonCoreResourceSpecification(apiGroup = "rbac.authorization.k8s.io",
      version = "v1beta1",
      scope = Scope.Cluster,
      names = Names(plural = "clusterroles",
        singular = "clusterrole",
        kind = "ClusterRole",
        shortNames = Nil))
  }

}
