package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._

case class NamespaceApplyConfig(
  kind: String = "Namespace",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None
) extends ApplyConfiguration[Namespace] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): NamespaceApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): NamespaceApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): NamespaceApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
}

object NamespaceApplyConfig {
  def apply(name: String): NamespaceApplyConfig =
    NamespaceApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[NamespaceApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig]
  )(n => (n.kind, n.apiVersion, n.metadata))
}
