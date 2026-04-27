package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.json.format._

case class LimitRangeSpecApplyConfig(
  items: Option[List[LimitRange.Item]] = None
) {
  def addItem(item: LimitRange.Item): LimitRangeSpecApplyConfig =
    copy(items = Some(item :: items.getOrElse(Nil)))
}

object LimitRangeSpecApplyConfig {
  implicit val writes: OWrites[LimitRangeSpecApplyConfig] =
    (JsPath \ "limits").writeNullable[List[LimitRange.Item]].contramap(_.items)
}

case class LimitRangeApplyConfig(
  kind: String = "LimitRange",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[LimitRangeSpecApplyConfig] = None
) extends ApplyConfiguration[LimitRange] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): LimitRangeApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): LimitRangeApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): LimitRangeApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: LimitRangeSpecApplyConfig): LimitRangeApplyConfig = copy(spec = Some(s))
}

object LimitRangeApplyConfig {
  def apply(name: String): LimitRangeApplyConfig =
    LimitRangeApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[LimitRangeApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[LimitRangeSpecApplyConfig]
  )(l => (l.kind, l.apiVersion, l.metadata, l.spec))
}
