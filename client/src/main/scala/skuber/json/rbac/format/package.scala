package skuber.json.rbac

import skuber._
import skuber.rbac._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import skuber.json.format._ // reuse some core formatters

/**
  * Created by jordan on 1/13/17.
  */
package object format {

  implicit val subjectFormat: Format[Subject] = (
    (JsPath \ "apiVersion").formatNullable[String] and
      (JsPath \ "kind").format[String] and
      (JsPath \ "name").format[String] and
      (JsPath \ "namespace").formatNullable[String]
    )(Subject.apply _, unlift(Subject.unapply))

  implicit val policyRuleFormat: Format[PolicyRule] = (
    (JsPath \ "apiGroups").formatMaybeEmptyList[String] and
    (JsPath \ "attributeRestrictions").formatNullable[String] and
    (JsPath \ "nonResourceURLs").formatMaybeEmptyList[String] and
    (JsPath \ "resourceNames").formatMaybeEmptyList[String] and
    (JsPath \ "resources").formatMaybeEmptyList[String] and
    (JsPath \ "verbs").formatMaybeEmptyList[String]
  )(PolicyRule.apply _, unlift(PolicyRule.unapply))

  implicit val roleFormat: Format[Role] = (
    objFormat and
    (JsPath \ "rules").formatMaybeEmptyList[PolicyRule]
  )(Role.apply _, unlift(Role.unapply))


  implicit val roleRefFormat: Format[RoleRef] = (
    (JsPath \ "apiGroup").format[String] and
    (JsPath \ "kind").format[String] and
    (JsPath \ "name").format[String]
  )(RoleRef.apply _, unlift(RoleRef.unapply))

  implicit val clusterRoleBindingFormat: Format[ClusterRoleBinding] = (
    objFormat and
      (JsPath \ "roleRef").formatNullable[RoleRef] and
      (JsPath \ "subjects").formatMaybeEmptyList[Subject]
    )(ClusterRoleBinding.apply _, unlift(ClusterRoleBinding.unapply))

  implicit val clusterRoleFormat: Format[ClusterRole] = (
    objFormat and
      (JsPath \ "rules").formatMaybeEmptyList[PolicyRule]
    )(ClusterRole.apply _, unlift(ClusterRole.unapply))

  implicit val roleBindingFormat: Format[RoleBinding] = (
    objFormat and
      (JsPath \ "roleRef").format[RoleRef] and
      (JsPath \ "subjects").formatMaybeEmptyList[Subject]
    )(RoleBinding.apply _, unlift(RoleBinding.unapply))

  implicit val roleListFormat: Format[RoleList] = KListFormat[Role].apply(RoleList.apply _, unlift(RoleList.unapply))
  implicit val roleBindingListFormat: Format[RoleBindingList] = KListFormat[RoleBinding].apply(RoleBindingList.apply _, unlift(RoleBindingList.unapply))
  implicit val clusterRoleListFormat: Format[ClusterRoleList] = KListFormat[ClusterRole].apply(ClusterRoleList.apply _, unlift(ClusterRoleList.unapply))
  implicit val clusterRoleBindingListFormat: Format[ClusterRoleBindingList] = KListFormat[ClusterRoleBinding].apply(ClusterRoleBindingList.apply _, unlift(ClusterRoleBindingList.unapply))
}
