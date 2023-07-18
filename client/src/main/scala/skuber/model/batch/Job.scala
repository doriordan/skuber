package skuber.model.batch

import skuber.model.ResourceSpecification.{Names, Scope}
import skuber.model._

/**
  * @author Cory Klein
  */
case class Job(
  kind: String = "Job",
  override val apiVersion: String = batchAPIVersion,
  metadata: ObjectMeta = ObjectMeta(),
  spec: Option[Job.Spec] = None,
  status: Option[Job.Status] = None)
    extends ObjectResource
{

  lazy val copySpec: Job.Spec = this.spec.getOrElse(Job.Spec())

  def withTemplate(template: Pod.Template.Spec) = this.copy(spec = Some(copySpec.copy(template = Some(template))))
  def withParallelism(parallelism: Int) = this.copy(spec = Some(copySpec.copy(parallelism = Some(parallelism))))
  def withCompletions(completions: Int) = this.copy(spec = Some(copySpec.copy(completions = Some(completions))))
  def withActiveDeadlineSeconds(seconds: Int) = this.copy(spec = Some(copySpec.copy(activeDeadlineSeconds = Some(seconds))))
  def withBackoffLimit(limit: Int) = this.copy(spec = Some(copySpec.copy(backoffLimit = Some(limit))))
  def withTTLSecondsAfterFinished(limit: Int) = this.copy(spec = Some(copySpec.copy(ttlSecondsAfterFinished = Some(limit))))
}

object Job {

  val specification=NonCoreResourceSpecification (
    apiGroup="batch",
    version="v1",
    scope = Scope.Namespaced,
    names=Names(
      plural = "jobs",
      singular = "job",
      kind = "Job",
      shortNames = Nil
    )
  )
  implicit val jobDef = new ResourceDefinition[Job] { def spec=specification }
  implicit val jobListDef = new ResourceDefinition[JobList] { def spec=specification }

  def apply(name: String) = new Job(metadata=ObjectMeta(name=name))

  case class Spec(parallelism: Option[Int] = None,
                  completions: Option[Int] = None,
                  activeDeadlineSeconds: Option[Long] = None,
                  selector: Option[LabelSelector] = None,
                  manualSelector: Option[Boolean] = None,
                  template: Option[Pod.Template.Spec] = None,
                  backoffLimit: Option[Int] = None,
                  ttlSecondsAfterFinished: Option[Int] = None)

  case class Status(conditions: List[Condition] = List(),
                    startTime: Option[Timestamp] = None,
                    completionTime: Option[Timestamp] = None,
                    active: Option[Int] = None,
                    succeeded: Option[Int] = None,
                    failed: Option[Int] = None)

  case class Condition(`type`: String = "",
                       status: String = "",
                       lastProbeTime: Option[Timestamp],
                       lastTransitionTime: Option[Timestamp],
                       reason: Option[String],
                       message: Option[String])
}
