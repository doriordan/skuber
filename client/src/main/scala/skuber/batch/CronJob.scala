package skuber.batch

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{ObjectReference, LabelSelector, NonCoreResourceSpecification, ObjectMeta, ObjectResource, Pod, ResourceDefinition, Timestamp}

/**
  * @author David O'Riordan
  */
case class CronJob(val kind: String ="CronJob",
  override val apiVersion: String = batchAPIVersion,
  val metadata: ObjectMeta = ObjectMeta(),
  spec: Option[CronJob.Spec] = None,
  status: Option[CronJob.Status] = None) extends ObjectResource

object CronJob {

  implicit val cronjobDef = new ResourceDefinition[CronJob] {
    def spec = NonCoreResourceSpecification(
      group=Some("batch"),
      version="v2alpha1",
      scope = Scope.Namespaced,
      names=Names(
        plural = "cronjobs",
        singular = "cronjob",
        kind = "CronJob",
        shortNames = Nil)
    )
  }

  def apply(name: String) = new CronJob(metadata=ObjectMeta(name=name))
  def apply(name: String, schedule: String, jobTemplateSpec: JobTemplate.Spec) =
    new CronJob(metadata=ObjectMeta(name=name),spec=Some(Spec(schedule=schedule, jobTemplate = jobTemplateSpec)))

  case class Spec(
    schedule: String,
    jobTemplate: JobTemplate.Spec,
    startingDeadlineSeconds: Option[Long] = None,
    concurrencyPolicy: Option[String] = None, // can be "Allow" (implied if None), "Forbid" or "Replace"
    suspend: Option[Boolean] = None,
    successfulJobsHistoryLimit: Option[Int] = None,
    failedJobsHistoryLimit: Option[Int] = None)

  case class Status(
    lastScheduleTime: Option[Timestamp],
    active: List[ObjectReference]
  )
}
