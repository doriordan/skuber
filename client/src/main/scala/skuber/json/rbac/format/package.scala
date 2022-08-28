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

  implicit val subjectFormat: Format[Subject] = ((JsPath \ "apiVersion").formatNullable[String] and
    (JsPath \ "kind").format[String] and
    (JsPath \ "name").format[String] and
    (JsPath \ "namespace").formatNullable[String]) (Subject.apply, s => (s.apiVersion, s.kind, s.name, s.namespace))

  implicit val policyRuleFormat: Format[PolicyRule] = ((JsPath \ "apiGroups").formatMaybeEmptyList[String] and
    (JsPath \ "attributeRestrictions").formatNullable[String] and
    (JsPath \ "nonResourceURLs").formatMaybeEmptyList[String] and
    (JsPath \ "resourceNames").formatMaybeEmptyList[String] and
    (JsPath \ "resources").formatMaybeEmptyList[String] and
    (JsPath \ "verbs").formatMaybeEmptyList[String]) (PolicyRule.apply, p => (p.apiGroups, p.attributeRestrictions, p.nonResourceURLs, p.resourceNames, p.resources, p.verbs))

  implicit val roleFormat: Format[Role] = (objFormat and
    (JsPath \ "rules").formatMaybeEmptyList[PolicyRule]) (Role.apply, r => (r.kind, r.apiVersion, r.metadata, r.rules))


  implicit val roleRefFormat: Format[RoleRef] = ((JsPath \ "apiGroup").format[String] and
    (JsPath \ "kind").format[String] and
    (JsPath \ "name").format[String]) (RoleRef.apply, r => (r.apiGroup, r.kind, r.name))

  implicit val clusterRoleBindingFormat: Format[ClusterRoleBinding] = (objFormat and
    (JsPath \ "roleRef").formatNullable[RoleRef] and
    (JsPath \ "subjects").formatMaybeEmptyList[Subject]) (ClusterRoleBinding.apply, c => (c.kind, c.apiVersion, c.metadata, c.roleRef, c.subjects))

  implicit val clusterRoleFormat: Format[ClusterRole] = (objFormat and
    (JsPath \ "rules").formatMaybeEmptyList[PolicyRule]) (ClusterRole.apply, c => (c.kind, c.apiVersion, c.metadata, c.rules))

  implicit val roleBindingFormat: Format[RoleBinding] = (objFormat and
    (JsPath \ "roleRef").format[RoleRef] and
    (JsPath \ "subjects").formatMaybeEmptyList[Subject]) (RoleBinding.apply, r => (r.kind, r.apiVersion, r.metadata, r.roleRef, r.subjects))

  implicit val roleListFormat: Format[RoleList] = ListResourceFormat[Role]
  implicit val roleBindingListFormat: Format[RoleBindingList] = ListResourceFormat[RoleBinding]
  implicit val clusterRoleListFormat: Format[ClusterRoleList] = ListResourceFormat[ClusterRole]
  implicit val clusterRoleBindingListFormat: Format[ClusterRoleBindingList] = ListResourceFormat[ClusterRoleBinding]
}
