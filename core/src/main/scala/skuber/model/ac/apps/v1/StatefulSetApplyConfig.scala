package skuber.model.ac.apps.v1

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.apps.v1.StatefulSet
import skuber.json.format._

case class StatefulSetSpecApplyConfig(
  replicas: Option[Int] = None,
  serviceName: Option[String] = None,
  selector: Option[LabelSelector] = None,
  template: Option[PodTemplateSpecApplyConfig] = None,
  volumeClaimTemplates: Option[List[PersistentVolumeClaimApplyConfig]] = None,
  updateStrategy: Option[StatefulSet.UpdateStrategy] = None,
  revisionHistoryLimit: Option[Int] = None
) {
  def withReplicas(n: Int): StatefulSetSpecApplyConfig = copy(replicas = Some(n))
  def withServiceName(sn: String): StatefulSetSpecApplyConfig = copy(serviceName = Some(sn))
  def withSelector(s: LabelSelector): StatefulSetSpecApplyConfig = copy(selector = Some(s))
  def withTemplate(t: PodTemplateSpecApplyConfig): StatefulSetSpecApplyConfig = copy(template = Some(t))
  def addVolumeClaimTemplate(vct: PersistentVolumeClaimApplyConfig): StatefulSetSpecApplyConfig =
    copy(volumeClaimTemplates = Some(vct :: volumeClaimTemplates.getOrElse(Nil)))
  def withUpdateStrategy(s: StatefulSet.UpdateStrategy): StatefulSetSpecApplyConfig = copy(updateStrategy = Some(s))
  def withRevisionHistoryLimit(l: Int): StatefulSetSpecApplyConfig = copy(revisionHistoryLimit = Some(l))
}

object StatefulSetSpecApplyConfig {
  implicit val writes: OWrites[StatefulSetSpecApplyConfig] = (
    (JsPath \ "replicas").writeNullable[Int] and
    (JsPath \ "serviceName").writeNullable[String] and
    (JsPath \ "selector").writeNullable[LabelSelector] and
    (JsPath \ "template").writeNullable[PodTemplateSpecApplyConfig] and
    (JsPath \ "volumeClaimTemplates").writeNullable[List[PersistentVolumeClaimApplyConfig]] and
    (JsPath \ "updateStrategy").writeNullable[StatefulSet.UpdateStrategy] and
    (JsPath \ "revisionHistoryLimit").writeNullable[Int]
  )(s => (s.replicas, s.serviceName, s.selector, s.template, s.volumeClaimTemplates, s.updateStrategy, s.revisionHistoryLimit))
}

case class StatefulSetApplyConfig(
  kind: String = "StatefulSet",
  apiVersion: String = "apps/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[StatefulSetSpecApplyConfig] = None
) extends ApplyConfiguration[StatefulSet] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): StatefulSetApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): StatefulSetApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): StatefulSetApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: StatefulSetSpecApplyConfig): StatefulSetApplyConfig = copy(spec = Some(s))
  def withReplicas(n: Int): StatefulSetApplyConfig = copy(spec = Some(spec.getOrElse(StatefulSetSpecApplyConfig()).withReplicas(n)))
}

object StatefulSetApplyConfig {
  def apply(name: String): StatefulSetApplyConfig =
    StatefulSetApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[StatefulSetApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[StatefulSetSpecApplyConfig]
  )(s => (s.kind, s.apiVersion, s.metadata, s.spec))
}
