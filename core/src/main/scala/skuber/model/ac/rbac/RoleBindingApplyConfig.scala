package skuber.model.ac.rbac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.authorization.rbac._
import skuber.json.format._
import skuber.json.authorization.rbac.format._

case class RoleBindingApplyConfig(
  kind: String = "RoleBinding",
  apiVersion: String = "rbac.authorization.k8s.io/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  roleRef: Option[RoleRef] = None,
  subjects: Option[List[Subject]] = None
) extends ApplyConfiguration[RoleBinding] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): RoleBindingApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): RoleBindingApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): RoleBindingApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withRoleRef(ref: RoleRef): RoleBindingApplyConfig = copy(roleRef = Some(ref))
  def addSubject(s: Subject): RoleBindingApplyConfig = copy(subjects = Some(s :: subjects.getOrElse(Nil)))
  def withSubjects(s: List[Subject]): RoleBindingApplyConfig = copy(subjects = Some(s))
}

object RoleBindingApplyConfig {
  def apply(name: String): RoleBindingApplyConfig =
    RoleBindingApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[RoleBindingApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "roleRef").writeNullable[RoleRef] and
    (JsPath \ "subjects").writeNullable[List[Subject]]
  )(r => (r.kind, r.apiVersion, r.metadata, r.roleRef, r.subjects))
}
