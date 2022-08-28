package skuber.json.batch

import play.api.libs.json.{Format, JsPath, Json}
import skuber.batch.{Job, JobList, JobTemplate, CronJob, CronJobList}

/**
 * Created by Cory Klein on 9/30/16.
 */
import play.api.libs.functional.syntax._
import skuber._
import skuber.json.format._ // reuse some core formatters

package object format {

  // Job formatters
  implicit val jobConditionFormat: Format[Job.Condition] = ((JsPath \ "type").formatMaybeEmptyString() and
    (JsPath \ "status").formatMaybeEmptyString() and
    (JsPath \ "lastProbeTime").formatNullable[Timestamp] and
    (JsPath \ "lastTransitionTime").formatNullable[Timestamp] and
    (JsPath \ "reason").formatNullable[String] and
    (JsPath \ "message").formatNullable[String]) (Job.Condition.apply, j => (j.`type`, j.status, j.lastProbeTime, j.lastTransitionTime, j.reason, j.message))

  implicit val jobStatusFormat: Format[Job.Status] = ((JsPath \ "conditions").formatMaybeEmptyList[Job.Condition] and
    (JsPath \ "startTime").formatNullable[Timestamp] and
    (JsPath \ "completionTime").formatNullable[Timestamp] and
    (JsPath \ "active").formatNullable[Int] and
    (JsPath \ "succeeded").formatNullable[Int] and
    (JsPath \ "failed").formatNullable[Int]) (Job.Status.apply, j => (j.conditions, j.startTime, j.completionTime, j.active, j.succeeded, j.failed))

  implicit val jobSpecFormat: Format[Job.Spec] = ((JsPath \ "parallelism").formatNullable[Int] and
    (JsPath \ "completions").formatNullable[Int] and
    (JsPath \ "activeDeadlineSeconds").formatNullable[Long] and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "manualSelector").formatNullable[Boolean] and
    (JsPath \ "template").formatNullable[Pod.Template.Spec] and
    (JsPath \ "backoffLimit").formatNullable[Int] and
    (JsPath \ "ttlSecondsAfterFinished").formatNullable[Int]) (Job.Spec.apply, j => (j.parallelism, j.completions, j.activeDeadlineSeconds, j.selector, j.manualSelector, j.template, j.backoffLimit, j.ttlSecondsAfterFinished))

  implicit val jobFormat: Format[Job] = ((JsPath \ "kind").formatMaybeEmptyString() and
    (JsPath \ "apiVersion").formatMaybeEmptyString() and
    (JsPath \ "metadata").format[ObjectMeta] and
    (JsPath \ "spec").formatNullable[Job.Spec] and
    (JsPath \ "status").formatNullable[Job.Status]) (Job.apply, j => (j.kind, j.apiVersion, j.metadata, j.spec, j.status))

  implicit val jobTmplSpecFmt: Format[JobTemplate.Spec] = Json.format[JobTemplate.Spec]

  implicit val cronJobSpecFmt: Format[CronJob.Spec] = Json.format[CronJob.Spec]

  implicit val cronJobStatusFmt: Format[CronJob.Status] = ((JsPath \ "lastScheduleTime").formatNullable[Timestamp] and
    (JsPath \ "active").formatMaybeEmptyList[ObjectReference]) (CronJob.Status.apply, c => (c.lastScheduleTime, c.active))

  implicit val cronJob: Format[CronJob] = ((JsPath \ "kind").formatMaybeEmptyString() and
    (JsPath \ "apiVersion").formatMaybeEmptyString() and
    (JsPath \ "metadata").format[ObjectMeta] and
    (JsPath \ "spec").formatNullable[CronJob.Spec] and
    (JsPath \ "status").formatNullable[CronJob.Status]) (CronJob.apply, c => (c.kind, c.apiVersion, c.metadata, c.spec, c.status))

  implicit val jobListFmt: Format[JobList] = ListResourceFormat[Job]
  implicit val cronJobListFmt: Format[CronJobList] = ListResourceFormat[CronJob]

}
