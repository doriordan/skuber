package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.json.format.mapStringByteArrayFormat

case class SecretApplyConfig(
  kind: String = "Secret",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  data: Option[Map[String, Array[Byte]]] = None,
  stringData: Option[Map[String, String]] = None,
  `type`: Option[String] = None
) extends ApplyConfiguration[Secret] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): SecretApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): SecretApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): SecretApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withData(d: Map[String, Array[Byte]]): SecretApplyConfig = copy(data = Some(d))
  def addData(kv: (String, Array[Byte])): SecretApplyConfig = copy(data = Some(data.getOrElse(Map.empty) + kv))
  def withStringData(d: Map[String, String]): SecretApplyConfig = copy(stringData = Some(d))
  def withType(t: String): SecretApplyConfig = copy(`type` = Some(t))
}

object SecretApplyConfig {
  def apply(name: String): SecretApplyConfig =
    SecretApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[SecretApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "data").writeNullable[Map[String, Array[Byte]]] and
    (JsPath \ "stringData").writeNullable[Map[String, String]] and
    (JsPath \ "type").writeNullable[String]
  )(s => (s.kind, s.apiVersion, s.metadata, s.data, s.stringData, s.`type`))
}
