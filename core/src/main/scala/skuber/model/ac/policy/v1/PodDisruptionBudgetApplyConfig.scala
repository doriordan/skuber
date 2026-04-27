package skuber.model.ac.policy.v1

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.policy.v1.PodDisruptionBudget
import skuber.json.format._

case class PodDisruptionBudgetSpecApplyConfig(
  maxUnavailable: Option[IntOrString] = None,
  minAvailable: Option[IntOrString] = None,
  selector: Option[LabelSelector] = None
) {
  def withMaxUnavailable(v: IntOrString): PodDisruptionBudgetSpecApplyConfig = copy(maxUnavailable = Some(v))
  def withMinAvailable(v: IntOrString): PodDisruptionBudgetSpecApplyConfig = copy(minAvailable = Some(v))
  def withSelector(s: LabelSelector): PodDisruptionBudgetSpecApplyConfig = copy(selector = Some(s))
}

object PodDisruptionBudgetSpecApplyConfig {
  implicit val writes: OWrites[PodDisruptionBudgetSpecApplyConfig] = OWrites { s =>
    Json.obj() ++
      s.maxUnavailable.fold(Json.obj())(v => Json.obj("maxUnavailable" -> v)) ++
      s.minAvailable.fold(Json.obj())(v => Json.obj("minAvailable" -> v)) ++
      s.selector.fold(Json.obj())(v => Json.obj("selector" -> Json.toJson(v)))
  }
}

case class PodDisruptionBudgetApplyConfig(
  kind: String = "PodDisruptionBudget",
  apiVersion: String = "policy/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[PodDisruptionBudgetSpecApplyConfig] = None
) extends ApplyConfiguration[PodDisruptionBudget] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): PodDisruptionBudgetApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): PodDisruptionBudgetApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): PodDisruptionBudgetApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: PodDisruptionBudgetSpecApplyConfig): PodDisruptionBudgetApplyConfig = copy(spec = Some(s))
}

object PodDisruptionBudgetApplyConfig {
  def apply(name: String): PodDisruptionBudgetApplyConfig =
    PodDisruptionBudgetApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[PodDisruptionBudgetApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[PodDisruptionBudgetSpecApplyConfig]
  )(p => (p.kind, p.apiVersion, p.metadata, p.spec))
}
