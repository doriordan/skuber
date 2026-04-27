package skuber.model.ac.batch

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.batch.Job
import skuber.json.format._

case class JobSpecApplyConfig(
  parallelism: Option[Int] = None,
  completions: Option[Int] = None,
  activeDeadlineSeconds: Option[Long] = None,
  selector: Option[LabelSelector] = None,
  manualSelector: Option[Boolean] = None,
  template: Option[PodTemplateSpecApplyConfig] = None,
  backoffLimit: Option[Int] = None,
  ttlSecondsAfterFinished: Option[Int] = None
) {
  def withParallelism(n: Int): JobSpecApplyConfig = copy(parallelism = Some(n))
  def withCompletions(n: Int): JobSpecApplyConfig = copy(completions = Some(n))
  def withActiveDeadlineSeconds(s: Long): JobSpecApplyConfig = copy(activeDeadlineSeconds = Some(s))
  def withSelector(s: LabelSelector): JobSpecApplyConfig = copy(selector = Some(s))
  def withTemplate(t: PodTemplateSpecApplyConfig): JobSpecApplyConfig = copy(template = Some(t))
  def withBackoffLimit(n: Int): JobSpecApplyConfig = copy(backoffLimit = Some(n))
  def withTTLSecondsAfterFinished(n: Int): JobSpecApplyConfig = copy(ttlSecondsAfterFinished = Some(n))
}

object JobSpecApplyConfig {
  implicit val writes: OWrites[JobSpecApplyConfig] = (
    (JsPath \ "parallelism").writeNullable[Int] and
    (JsPath \ "completions").writeNullable[Int] and
    (JsPath \ "activeDeadlineSeconds").writeNullable[Long] and
    (JsPath \ "selector").writeNullable[LabelSelector] and
    (JsPath \ "manualSelector").writeNullable[Boolean] and
    (JsPath \ "template").writeNullable[PodTemplateSpecApplyConfig] and
    (JsPath \ "backoffLimit").writeNullable[Int] and
    (JsPath \ "ttlSecondsAfterFinished").writeNullable[Int]
  )(s => (s.parallelism, s.completions, s.activeDeadlineSeconds, s.selector, s.manualSelector, s.template, s.backoffLimit, s.ttlSecondsAfterFinished))
}

case class JobApplyConfig(
  kind: String = "Job",
  apiVersion: String = "batch/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[JobSpecApplyConfig] = None
) extends ApplyConfiguration[Job] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): JobApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): JobApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): JobApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: JobSpecApplyConfig): JobApplyConfig = copy(spec = Some(s))
}

object JobApplyConfig {
  def apply(name: String): JobApplyConfig =
    JobApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[JobApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[JobSpecApplyConfig]
  )(j => (j.kind, j.apiVersion, j.metadata, j.spec))
}
