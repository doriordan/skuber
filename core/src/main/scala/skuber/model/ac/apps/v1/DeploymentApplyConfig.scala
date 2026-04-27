package skuber.model.ac.apps.v1

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.apps.v1.Deployment
import skuber.json.format._

case class DeploymentSpecApplyConfig(
  replicas: Option[Int] = None,
  selector: Option[LabelSelector] = None,
  template: Option[PodTemplateSpecApplyConfig] = None,
  strategy: Option[Deployment.Strategy] = None,
  minReadySeconds: Option[Int] = None,
  revisionHistoryLimit: Option[Int] = None,
  paused: Option[Boolean] = None,
  progressDeadlineSeconds: Option[Int] = None
) {
  def withReplicas(n: Int): DeploymentSpecApplyConfig = copy(replicas = Some(n))
  def withSelector(s: LabelSelector): DeploymentSpecApplyConfig = copy(selector = Some(s))
  def withTemplate(t: PodTemplateSpecApplyConfig): DeploymentSpecApplyConfig = copy(template = Some(t))
  def withStrategy(s: Deployment.Strategy): DeploymentSpecApplyConfig = copy(strategy = Some(s))
  def withMinReadySeconds(s: Int): DeploymentSpecApplyConfig = copy(minReadySeconds = Some(s))
  def withRevisionHistoryLimit(l: Int): DeploymentSpecApplyConfig = copy(revisionHistoryLimit = Some(l))
  def withPaused(p: Boolean): DeploymentSpecApplyConfig = copy(paused = Some(p))
  def withProgressDeadlineSeconds(s: Int): DeploymentSpecApplyConfig = copy(progressDeadlineSeconds = Some(s))
}

object DeploymentSpecApplyConfig {
  implicit val writes: OWrites[DeploymentSpecApplyConfig] = (
    (JsPath \ "replicas").writeNullable[Int] and
    (JsPath \ "selector").writeNullable[LabelSelector] and
    (JsPath \ "template").writeNullable[PodTemplateSpecApplyConfig] and
    (JsPath \ "strategy").writeNullable[Deployment.Strategy] and
    (JsPath \ "minReadySeconds").writeNullable[Int] and
    (JsPath \ "revisionHistoryLimit").writeNullable[Int] and
    (JsPath \ "paused").writeNullable[Boolean] and
    (JsPath \ "progressDeadlineSeconds").writeNullable[Int]
  )(d => (d.replicas, d.selector, d.template, d.strategy, d.minReadySeconds, d.revisionHistoryLimit, d.paused, d.progressDeadlineSeconds))
}

case class DeploymentApplyConfig(
  kind: String = "Deployment",
  apiVersion: String = "apps/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[DeploymentSpecApplyConfig] = None
) extends ApplyConfiguration[Deployment] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): DeploymentApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): DeploymentApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): DeploymentApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: DeploymentSpecApplyConfig): DeploymentApplyConfig = copy(spec = Some(s))
  def withReplicas(count: Int): DeploymentApplyConfig = copy(spec = Some(spec.getOrElse(DeploymentSpecApplyConfig()).copy(replicas = Some(count))))
}

object DeploymentApplyConfig {
  def apply(name: String): DeploymentApplyConfig =
    DeploymentApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[DeploymentApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[DeploymentSpecApplyConfig]
  )(d => (d.kind, d.apiVersion, d.metadata, d.spec))
}
