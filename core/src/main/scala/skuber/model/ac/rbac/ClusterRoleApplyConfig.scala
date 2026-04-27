package skuber.model.ac.rbac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.authorization.rbac._
import skuber.json.format._
import skuber.json.authorization.rbac.format._

case class ClusterRoleApplyConfig(
  kind: String = "ClusterRole",
  apiVersion: String = "rbac.authorization.k8s.io/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  rules: Option[List[PolicyRule]] = None
) extends ApplyConfiguration[ClusterRole] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): ClusterRoleApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): ClusterRoleApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): ClusterRoleApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def addRule(rule: PolicyRule): ClusterRoleApplyConfig = copy(rules = Some(rule :: rules.getOrElse(Nil)))
  def withRules(r: List[PolicyRule]): ClusterRoleApplyConfig = copy(rules = Some(r))
}

object ClusterRoleApplyConfig {
  def apply(name: String): ClusterRoleApplyConfig =
    ClusterRoleApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[ClusterRoleApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "rules").writeNullable[List[PolicyRule]]
  )(r => (r.kind, r.apiVersion, r.metadata, r.rules))
}
