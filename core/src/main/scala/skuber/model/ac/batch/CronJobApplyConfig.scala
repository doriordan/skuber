package skuber.model.ac.batch

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.batch.CronJob

case class JobTemplateSpecApplyConfig(
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[JobSpecApplyConfig] = None
) {
  def withMetadata(m: ObjectMetaApplyConfig): JobTemplateSpecApplyConfig = copy(metadata = Some(m))
  def withSpec(s: JobSpecApplyConfig): JobTemplateSpecApplyConfig = copy(spec = Some(s))
}

object JobTemplateSpecApplyConfig {
  implicit val writes: OWrites[JobTemplateSpecApplyConfig] = (
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[JobSpecApplyConfig]
  )(t => (t.metadata, t.spec))
}

case class CronJobSpecApplyConfig(
  schedule: Option[String] = None,
  jobTemplate: Option[JobTemplateSpecApplyConfig] = None,
  startingDeadlineSeconds: Option[Long] = None,
  concurrencyPolicy: Option[String] = None,
  suspend: Option[Boolean] = None,
  successfulJobsHistoryLimit: Option[Int] = None,
  failedJobsHistoryLimit: Option[Int] = None
) {
  def withSchedule(s: String): CronJobSpecApplyConfig = copy(schedule = Some(s))
  def withJobTemplate(t: JobTemplateSpecApplyConfig): CronJobSpecApplyConfig = copy(jobTemplate = Some(t))
  def withStartingDeadlineSeconds(s: Long): CronJobSpecApplyConfig = copy(startingDeadlineSeconds = Some(s))
  def withConcurrencyPolicy(p: String): CronJobSpecApplyConfig = copy(concurrencyPolicy = Some(p))
  def withSuspend(s: Boolean): CronJobSpecApplyConfig = copy(suspend = Some(s))
  def withSuccessfulJobsHistoryLimit(l: Int): CronJobSpecApplyConfig = copy(successfulJobsHistoryLimit = Some(l))
  def withFailedJobsHistoryLimit(l: Int): CronJobSpecApplyConfig = copy(failedJobsHistoryLimit = Some(l))
}

object CronJobSpecApplyConfig {
  implicit val writes: OWrites[CronJobSpecApplyConfig] = (
    (JsPath \ "schedule").writeNullable[String] and
    (JsPath \ "jobTemplate").writeNullable[JobTemplateSpecApplyConfig] and
    (JsPath \ "startingDeadlineSeconds").writeNullable[Long] and
    (JsPath \ "concurrencyPolicy").writeNullable[String] and
    (JsPath \ "suspend").writeNullable[Boolean] and
    (JsPath \ "successfulJobsHistoryLimit").writeNullable[Int] and
    (JsPath \ "failedJobsHistoryLimit").writeNullable[Int]
  )(s => (s.schedule, s.jobTemplate, s.startingDeadlineSeconds, s.concurrencyPolicy, s.suspend, s.successfulJobsHistoryLimit, s.failedJobsHistoryLimit))
}

case class CronJobApplyConfig(
  kind: String = "CronJob",
  apiVersion: String = "batch/v1beta1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[CronJobSpecApplyConfig] = None
) extends ApplyConfiguration[CronJob] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): CronJobApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): CronJobApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): CronJobApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: CronJobSpecApplyConfig): CronJobApplyConfig = copy(spec = Some(s))
}

object CronJobApplyConfig {
  def apply(name: String): CronJobApplyConfig =
    CronJobApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[CronJobApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[CronJobSpecApplyConfig]
  )(c => (c.kind, c.apiVersion, c.metadata, c.spec))
}
