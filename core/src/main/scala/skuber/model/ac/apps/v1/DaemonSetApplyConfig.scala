package skuber.model.ac.apps.v1

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.apps.v1.DaemonSet
import skuber.json.format._

case class DaemonSetSpecApplyConfig(
  selector: Option[LabelSelector] = None,
  template: Option[PodTemplateSpecApplyConfig] = None,
  minReadySeconds: Option[Int] = None,
  updateStrategy: Option[DaemonSet.UpdateStrategy] = None,
  revisionHistoryLimit: Option[Int] = None
) {
  def withSelector(s: LabelSelector): DaemonSetSpecApplyConfig = copy(selector = Some(s))
  def withTemplate(t: PodTemplateSpecApplyConfig): DaemonSetSpecApplyConfig = copy(template = Some(t))
  def withMinReadySeconds(s: Int): DaemonSetSpecApplyConfig = copy(minReadySeconds = Some(s))
  def withUpdateStrategy(s: DaemonSet.UpdateStrategy): DaemonSetSpecApplyConfig = copy(updateStrategy = Some(s))
  def withRevisionHistoryLimit(l: Int): DaemonSetSpecApplyConfig = copy(revisionHistoryLimit = Some(l))
}

object DaemonSetSpecApplyConfig {
  implicit val writes: OWrites[DaemonSetSpecApplyConfig] = (
    (JsPath \ "selector").writeNullable[LabelSelector] and
    (JsPath \ "template").writeNullable[PodTemplateSpecApplyConfig] and
    (JsPath \ "minReadySeconds").writeNullable[Int] and
    (JsPath \ "updateStrategy").writeNullable[DaemonSet.UpdateStrategy] and
    (JsPath \ "revisionHistoryLimit").writeNullable[Int]
  )(d => (d.selector, d.template, d.minReadySeconds, d.updateStrategy, d.revisionHistoryLimit))
}

case class DaemonSetApplyConfig(
  kind: String = "DaemonSet",
  apiVersion: String = "apps/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[DaemonSetSpecApplyConfig] = None
) extends ApplyConfiguration[DaemonSet] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): DaemonSetApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): DaemonSetApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): DaemonSetApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: DaemonSetSpecApplyConfig): DaemonSetApplyConfig = copy(spec = Some(s))
}

object DaemonSetApplyConfig {
  def apply(name: String): DaemonSetApplyConfig =
    DaemonSetApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[DaemonSetApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[DaemonSetSpecApplyConfig]
  )(d => (d.kind, d.apiVersion, d.metadata, d.spec))
}
