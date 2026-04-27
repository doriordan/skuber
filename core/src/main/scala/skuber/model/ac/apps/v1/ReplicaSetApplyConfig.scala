package skuber.model.ac.apps.v1

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.apps.v1.ReplicaSet
import skuber.json.format._

case class ReplicaSetSpecApplyConfig(
  replicas: Option[Int] = None,
  minReadySeconds: Option[Int] = None,
  selector: Option[LabelSelector] = None,
  template: Option[PodTemplateSpecApplyConfig] = None
) {
  def withReplicas(n: Int): ReplicaSetSpecApplyConfig = copy(replicas = Some(n))
  def withMinReadySeconds(s: Int): ReplicaSetSpecApplyConfig = copy(minReadySeconds = Some(s))
  def withSelector(s: LabelSelector): ReplicaSetSpecApplyConfig = copy(selector = Some(s))
  def withTemplate(t: PodTemplateSpecApplyConfig): ReplicaSetSpecApplyConfig = copy(template = Some(t))
}

object ReplicaSetSpecApplyConfig {
  implicit val writes: OWrites[ReplicaSetSpecApplyConfig] = (
    (JsPath \ "replicas").writeNullable[Int] and
    (JsPath \ "minReadySeconds").writeNullable[Int] and
    (JsPath \ "selector").writeNullable[LabelSelector] and
    (JsPath \ "template").writeNullable[PodTemplateSpecApplyConfig]
  )(s => (s.replicas, s.minReadySeconds, s.selector, s.template))
}

case class ReplicaSetApplyConfig(
  kind: String = "ReplicaSet",
  apiVersion: String = "apps/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[ReplicaSetSpecApplyConfig] = None
) extends ApplyConfiguration[ReplicaSet] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): ReplicaSetApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): ReplicaSetApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): ReplicaSetApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: ReplicaSetSpecApplyConfig): ReplicaSetApplyConfig = copy(spec = Some(s))
  def withReplicas(n: Int): ReplicaSetApplyConfig = copy(spec = Some(spec.getOrElse(ReplicaSetSpecApplyConfig()).withReplicas(n)))
}

object ReplicaSetApplyConfig {
  def apply(name: String): ReplicaSetApplyConfig =
    ReplicaSetApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[ReplicaSetApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[ReplicaSetSpecApplyConfig]
  )(r => (r.kind, r.apiVersion, r.metadata, r.spec))
}
