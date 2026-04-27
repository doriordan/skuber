package skuber.model.ac.rbac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.authorization.rbac._
import skuber.json.format._
import skuber.json.authorization.rbac.format._

case class RoleApplyConfig(
  kind: String = "Role",
  apiVersion: String = "rbac.authorization.k8s.io/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  rules: Option[List[PolicyRule]] = None
) extends ApplyConfiguration[Role] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): RoleApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): RoleApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): RoleApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def addRule(rule: PolicyRule): RoleApplyConfig = copy(rules = Some(rule :: rules.getOrElse(Nil)))
  def withRules(r: List[PolicyRule]): RoleApplyConfig = copy(rules = Some(r))
}

object RoleApplyConfig {
  def apply(name: String): RoleApplyConfig =
    RoleApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[RoleApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "rules").writeNullable[List[PolicyRule]]
  )(r => (r.kind, r.apiVersion, r.metadata, r.rules))
}
