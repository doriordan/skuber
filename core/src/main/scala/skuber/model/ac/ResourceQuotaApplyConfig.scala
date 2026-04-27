package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.json.format._

case class ResourceQuotaSpecApplyConfig(
  hard: Option[Resource.ResourceList] = None
) {
  def withHard(h: Resource.ResourceList): ResourceQuotaSpecApplyConfig = copy(hard = Some(h))
  def addHard(kv: (String, Resource.Quantity)): ResourceQuotaSpecApplyConfig =
    copy(hard = Some(hard.getOrElse(Map.empty) + kv))
}

object ResourceQuotaSpecApplyConfig {
  implicit val writes: OWrites[ResourceQuotaSpecApplyConfig] =
    (JsPath \ "hard").writeNullable[Resource.ResourceList].contramap(_.hard)
}

case class ResourceQuotaApplyConfig(
  kind: String = "ResourceQuota",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[ResourceQuotaSpecApplyConfig] = None
) extends ApplyConfiguration[Resource.Quota] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): ResourceQuotaApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): ResourceQuotaApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): ResourceQuotaApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: ResourceQuotaSpecApplyConfig): ResourceQuotaApplyConfig = copy(spec = Some(s))
}

object ResourceQuotaApplyConfig {
  def apply(name: String): ResourceQuotaApplyConfig =
    ResourceQuotaApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[ResourceQuotaApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[ResourceQuotaSpecApplyConfig]
  )(r => (r.kind, r.apiVersion, r.metadata, r.spec))
}
