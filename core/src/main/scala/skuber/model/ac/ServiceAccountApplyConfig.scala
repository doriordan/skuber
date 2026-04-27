package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.json.format._

case class ServiceAccountApplyConfig(
  kind: String = "ServiceAccount",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  secrets: Option[List[ObjectReference]] = None,
  imagePullSecrets: Option[List[LocalObjectReference]] = None
) extends ApplyConfiguration[ServiceAccount] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): ServiceAccountApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): ServiceAccountApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): ServiceAccountApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def addImagePullSecret(name: String): ServiceAccountApplyConfig =
    copy(imagePullSecrets = Some(LocalObjectReference(name) :: imagePullSecrets.getOrElse(Nil)))
}

object ServiceAccountApplyConfig {
  def apply(name: String): ServiceAccountApplyConfig =
    ServiceAccountApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[ServiceAccountApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "secrets").writeNullable[List[ObjectReference]] and
    (JsPath \ "imagePullSecrets").writeNullable[List[LocalObjectReference]]
  )(s => (s.kind, s.apiVersion, s.metadata, s.secrets, s.imagePullSecrets))
}
