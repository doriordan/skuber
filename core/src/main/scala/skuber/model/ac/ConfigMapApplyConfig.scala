package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.json.format.mapStringByteArrayFormat

case class ConfigMapApplyConfig(
  kind: String = "ConfigMap",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  data: Option[Map[String, String]] = None,
  binaryData: Option[Map[String, Array[Byte]]] = None
) extends ApplyConfiguration[ConfigMap] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withData(d: Map[String, String]): ConfigMapApplyConfig = copy(data = Some(d))
  def addData(kv: (String, String)): ConfigMapApplyConfig = copy(data = Some(data.getOrElse(Map.empty) + kv))
  def withBinaryData(d: Map[String, Array[Byte]]): ConfigMapApplyConfig = copy(binaryData = Some(d))
}

object ConfigMapApplyConfig {
  def apply(name: String): ConfigMapApplyConfig =
    ConfigMapApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[ConfigMapApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "data").writeNullable[Map[String, String]] and
    (JsPath \ "binaryData").writeNullable[Map[String, Array[Byte]]]
  )(c => (c.kind, c.apiVersion, c.metadata, c.data, c.binaryData))
}
