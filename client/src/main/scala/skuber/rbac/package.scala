package skuber

import skuber.api.client._
import skuber.json.rbac.format._

/**
  * Created by jordan on 1/13/17.
  */
package object rbac {
  val rbacAPIVersion = "rbac.authorization.k8s.io/v1alpha1"

  trait isRBACKind[T <: TypeMeta] { self: Kind[T] =>
    override def isRBACKind = true
  }

  implicit val clusterRoleKind: ObjKind[ClusterRole] = new ObjKind[ClusterRole]("clusterroles", "ClusterRole") with isRBACKind[ClusterRole] { override def isNamespaced: Boolean = false }

  implicit val clusterRoleBindingKind: ObjKind[ClusterRoleBinding] = new ObjKind[ClusterRoleBinding]("clusterrolebindings", "ClusterRoleBinding") with isRBACKind[ClusterRoleBinding] { override def isNamespaced: Boolean = false }

  implicit val roleKind: ObjKind[Role] = new ObjKind[Role]("roles", "Role") with isRBACKind[Role]

  implicit val roleBindingKind: ObjKind[RoleBinding] = new ObjKind[RoleBinding]("rolebindings", "RoleBinding") with isRBACKind[RoleBinding]

  case class ClusterRoleList(
      val kind: String = "ClusterRoleList",
      override val apiVersion: String = rbacAPIVersion,
      val metadata: Option[ListMeta],
      val items: List[ClusterRole]) extends KList[ClusterRole]
  implicit val clusterRoleListKind = new ListKind[ClusterRoleList]("clusterroles") with isRBACKind[ClusterRoleList] { override def isNamespaced: Boolean = false }

  case class ClusterRoleBindingList(
      val kind: String = "ClusterRoleBindingList",
      override val apiVersion: String = rbacAPIVersion,
      val metadata: Option[ListMeta],
      val items: List[ClusterRoleBinding]) extends KList[ClusterRoleBinding]
  implicit val clusterRoleBindingListKind = new ListKind[ClusterRoleBindingList]("clusterrolebindings") with isRBACKind[ClusterRoleBindingList] { override def isNamespaced: Boolean = false }

  case class RoleList(
      val kind: String = "RoleList",
      override val apiVersion: String = rbacAPIVersion,
      val metadata: Option[ListMeta],
      val items: List[Role]) extends KList[Role]
  implicit val roleListKind = new ListKind[RoleList]("roles") with isRBACKind[RoleList]

  case class RoleBindingList(
      val kind: String = "RoleBindingList",
      override val apiVersion: String = rbacAPIVersion,
      val metadata: Option[ListMeta],
      val items: List[RoleBinding]) extends KList[RoleBinding]
  implicit val roleBindingListKind = new ListKind[RoleBindingList]("rolebindings") with isRBACKind[RoleBindingList]

}
