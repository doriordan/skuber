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
  implicit val jobConditionFormat: Format[Job.Condition] = (
      (JsPath \ "type").formatMaybeEmptyString() and
      (JsPath \ "status").formatMaybeEmptyString() and
      (JsPath \ "lastProbeTime").formatNullable[Timestamp] and
      (JsPath \ "lastTransitionTime").formatNullable[Timestamp] and
      (JsPath \ "reason").formatNullable[String] and
      (JsPath \ "message").formatNullable[String]
    )(Job.Condition.apply _, unlift(Job.Condition.unapply))

  implicit val jobStatusFormat: Format[Job.Status] = (
      (JsPath \ "conditions").formatNullable[Job.Condition] and
      (JsPath \ "startTime").formatNullable[Timestamp] and
      (JsPath \ "completionTime").formatNullable[Timestamp] and
      (JsPath \ "active").formatNullable[Int] and
      (JsPath \ "succeeded").formatNullable[Int] and
      (JsPath \ "failed").formatNullable[Int]
    )(Job.Status.apply _, unlift(Job.Status.unapply))

  implicit val jobSpecFormat: Format[Job.Spec] = (
      (JsPath \ "parallelism").formatNullable[Int] and
      (JsPath \ "completions").formatNullable[Int] and
      (JsPath \ "activeDeadlineSeconds").formatNullable[Long] and
      (JsPath \ "selector").formatNullableLabelSelector and
      (JsPath \ "manualSelector").formatNullable[Boolean] and
      (JsPath \ "template").formatNullable[Pod.Template.Spec]
    )(Job.Spec.apply _, unlift(Job.Spec.unapply))

  implicit val jobFormat: Format[Job] = (
      (JsPath \ "kind").format[String] and
      (JsPath \ "apiVersion").format[String] and
      (JsPath \ "metadata").format[ObjectMeta] and
      (JsPath \ "spec").formatNullable[Job.Spec] and
      (JsPath \ "status").formatNullable[Job.Status]
    )(Job.apply _, unlift(Job.unapply))

  implicit val jobTmplSpecFmt: Format[JobTemplate.Spec] = Json.format[JobTemplate.Spec]

  implicit val cronJobSpecFmt:Format[CronJob.Spec] = Json.format[CronJob.Spec]

  implicit val cronJobStatusFmt: Format[CronJob.Status] = (
      (JsPath \ "lastScheduleTime").formatNullable[Timestamp] and
      (JsPath \ "active").formatMaybeEmptyList[ObjectReference]
  )(CronJob.Status.apply _, unlift(CronJob.Status.unapply))

  implicit val cronJob: Format[CronJob] = (
      (JsPath \ "kind").format[String] and
      (JsPath \ "apiVersion").format[String] and
      (JsPath \ "metadata").format[ObjectMeta] and
      (JsPath \ "spec").formatNullable[CronJob.Spec] and
      (JsPath \ "status").formatNullable[CronJob.Status]
  )(CronJob.apply _, unlift(CronJob.unapply))

  implicit val jobListFmt: Format[JobList] = ListResourceFormat[Job]
  implicit val cronJobListFmt: Format[CronJobList] = ListResourceFormat[CronJob]

}
