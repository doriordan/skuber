package skuber.model.ac.rbac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.authorization.rbac._
import skuber.json.format._
import skuber.json.authorization.rbac.format._

case class ClusterRoleBindingApplyConfig(
  kind: String = "ClusterRoleBinding",
  apiVersion: String = "rbac.authorization.k8s.io/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  roleRef: Option[RoleRef] = None,
  subjects: Option[List[Subject]] = None
) extends ApplyConfiguration[ClusterRoleBinding] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): ClusterRoleBindingApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): ClusterRoleBindingApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): ClusterRoleBindingApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withRoleRef(ref: RoleRef): ClusterRoleBindingApplyConfig = copy(roleRef = Some(ref))
  def addSubject(s: Subject): ClusterRoleBindingApplyConfig = copy(subjects = Some(s :: subjects.getOrElse(Nil)))
  def withSubjects(s: List[Subject]): ClusterRoleBindingApplyConfig = copy(subjects = Some(s))
}

object ClusterRoleBindingApplyConfig {
  def apply(name: String): ClusterRoleBindingApplyConfig =
    ClusterRoleBindingApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[ClusterRoleBindingApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "roleRef").writeNullable[RoleRef] and
    (JsPath \ "subjects").writeNullable[List[Subject]]
  )(r => (r.kind, r.apiVersion, r.metadata, r.roleRef, r.subjects))
}
