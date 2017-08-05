package skuber.batch

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{LabelSelector, NonCoreResourceSpecification, ObjectMeta, ObjectResource, Pod, ResourceDefinition, Timestamp}

/**
  * @author Cory Klein
  */
case class Job(val kind: String ="Job",
               override val apiVersion: String = batchAPIVersion,
               val metadata: ObjectMeta = ObjectMeta(),
               spec: Option[Job.Spec] = None,
               status: Option[Job.Status] = None) extends ObjectResource {

  lazy val copySpec: Job.Spec = this.spec.getOrElse(new Job.Spec())

  def withTemplate(template: Pod.Template.Spec) = this.copy(spec=Some(copySpec.copy(template=Some(template))))
}

object Job {

  val specification=NonCoreResourceSpecification (
    group=Some("batch"),
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
                  template: Option[Pod.Template.Spec] = None)

  case class Status(conditions: Option[Condition] = None,
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
