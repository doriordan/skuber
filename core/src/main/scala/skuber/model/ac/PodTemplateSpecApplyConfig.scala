package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._

case class PodTemplateSpecApplyConfig(
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[PodSpecApplyConfig] = None
) {
  def addLabel(kv: (String, String)): PodTemplateSpecApplyConfig =
    copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addLabels(labels: Map[String, String]): PodTemplateSpecApplyConfig =
    copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).withLabels(
      metadata.flatMap(_.labels).getOrElse(Map.empty) ++ labels
    )))
  def addAnnotation(kv: (String, String)): PodTemplateSpecApplyConfig =
    copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def addContainer(c: ContainerApplyConfig): PodTemplateSpecApplyConfig =
    copy(spec = Some(spec.getOrElse(PodSpecApplyConfig()).addContainer(c)))
  def addInitContainer(c: ContainerApplyConfig): PodTemplateSpecApplyConfig =
    copy(spec = Some(spec.getOrElse(PodSpecApplyConfig()).addInitContainer(c)))
  def addVolume(v: Volume): PodTemplateSpecApplyConfig =
    copy(spec = Some(spec.getOrElse(PodSpecApplyConfig()).addVolume(v)))
  def withServiceAccountName(san: String): PodTemplateSpecApplyConfig =
    copy(spec = Some(spec.getOrElse(PodSpecApplyConfig()).withServiceAccountName(san)))
  def withRestartPolicy(rp: RestartPolicy.RestartPolicy): PodTemplateSpecApplyConfig =
    copy(spec = Some(spec.getOrElse(PodSpecApplyConfig()).withRestartPolicy(rp)))
  def withPodSpec(s: PodSpecApplyConfig): PodTemplateSpecApplyConfig = copy(spec = Some(s))
}

object PodTemplateSpecApplyConfig {
  implicit val writes: OWrites[PodTemplateSpecApplyConfig] = (
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[PodSpecApplyConfig]
  )(t => (t.metadata, t.spec))
}
