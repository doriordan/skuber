package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._

case class ReplicationControllerSpecApplyConfig(
  replicas: Option[Int] = None,
  selector: Option[Map[String, String]] = None,
  template: Option[PodTemplateSpecApplyConfig] = None
) {
  def withReplicas(n: Int): ReplicationControllerSpecApplyConfig = copy(replicas = Some(n))
  def withSelector(s: Map[String, String]): ReplicationControllerSpecApplyConfig = copy(selector = Some(s))
  def withTemplate(t: PodTemplateSpecApplyConfig): ReplicationControllerSpecApplyConfig = copy(template = Some(t))
}

object ReplicationControllerSpecApplyConfig {
  implicit val writes: OWrites[ReplicationControllerSpecApplyConfig] = (
    (JsPath \ "replicas").writeNullable[Int] and
    (JsPath \ "selector").writeNullable[Map[String, String]] and
    (JsPath \ "template").writeNullable[PodTemplateSpecApplyConfig]
  )(s => (s.replicas, s.selector, s.template))
}

case class ReplicationControllerApplyConfig(
  kind: String = "ReplicationController",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[ReplicationControllerSpecApplyConfig] = None
) extends ApplyConfiguration[ReplicationController] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): ReplicationControllerApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): ReplicationControllerApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): ReplicationControllerApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: ReplicationControllerSpecApplyConfig): ReplicationControllerApplyConfig = copy(spec = Some(s))
  def withReplicas(n: Int): ReplicationControllerApplyConfig = copy(spec = Some(spec.getOrElse(ReplicationControllerSpecApplyConfig()).withReplicas(n)))
}

object ReplicationControllerApplyConfig {
  def apply(name: String): ReplicationControllerApplyConfig =
    ReplicationControllerApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[ReplicationControllerApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[ReplicationControllerSpecApplyConfig]
  )(r => (r.kind, r.apiVersion, r.metadata, r.spec))
}
