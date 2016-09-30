package skuber.json.batch

import play.api.libs.json.{Format, JsPath}
import skuber.batch.{Job, JobList}

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

  implicit val jobListFmt: Format[JobList] = KListFormat[Job].apply(JobList.apply _, unapply(JobList.unapply))
}
