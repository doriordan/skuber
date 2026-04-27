package skuber.model.ac.autoscaling.v2

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.autoscaling.v2.HorizontalPodAutoscaler
import skuber.model.autoscaling.v2.HorizontalPodAutoscaler._

case class HPASpecApplyConfig(
  scaleTargetRef: Option[CrossVersionObjectReference] = None,
  minReplicas: Option[Int] = None,
  maxReplicas: Option[Int] = None,
  metrics: Option[List[Metric]] = None,
  behavior: Option[Spec.Behavior] = None
) {
  def withScaleTargetRef(ref: CrossVersionObjectReference): HPASpecApplyConfig = copy(scaleTargetRef = Some(ref))
  def withMinReplicas(n: Int): HPASpecApplyConfig = copy(minReplicas = Some(n))
  def withMaxReplicas(n: Int): HPASpecApplyConfig = copy(maxReplicas = Some(n))
  def addMetric(m: Metric): HPASpecApplyConfig = copy(metrics = Some(metrics.getOrElse(Nil) :+ m))
  def withMetrics(m: List[Metric]): HPASpecApplyConfig = copy(metrics = Some(m))
  def withBehavior(b: Spec.Behavior): HPASpecApplyConfig = copy(behavior = Some(b))
}

object HPASpecApplyConfig {
  implicit val writes: OWrites[HPASpecApplyConfig] = (
    (JsPath \ "scaleTargetRef").writeNullable[CrossVersionObjectReference] and
    (JsPath \ "minReplicas").writeNullable[Int] and
    (JsPath \ "maxReplicas").writeNullable[Int] and
    (JsPath \ "metrics").writeNullable[List[Metric]] and
    (JsPath \ "behavior").writeNullable[Spec.Behavior]
  )(s => (s.scaleTargetRef, s.minReplicas, s.maxReplicas, s.metrics, s.behavior))
}

case class HPAApplyConfig(
  kind: String = "HorizontalPodAutoscaler",
  apiVersion: String = "autoscaling/v2",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[HPASpecApplyConfig] = None
) extends ApplyConfiguration[HorizontalPodAutoscaler] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): HPAApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): HPAApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): HPAApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: HPASpecApplyConfig): HPAApplyConfig = copy(spec = Some(s))
}

object HPAApplyConfig {
  def apply(name: String): HPAApplyConfig =
    HPAApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[HPAApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[HPASpecApplyConfig]
  )(h => (h.kind, h.apiVersion, h.metadata, h.spec))
}
