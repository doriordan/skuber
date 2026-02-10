package skuber.examples.kronjob

import scala.annotation.experimental
import skuber.operator.crd.{CustomResourceDef, Scope, customResource}
import skuber.model.ObjectReference
import skuber.json.format.objRefFormat
import java.time.ZonedDateTime

/**
 *
 * This demonstrates creating a custom resource that manages Kubernetes Jobs
 * based on a cron schedule.
 *
 * Inspired by the kubebuilder tutorial, but here named "KronJob" to avoid confusion with
 * the analagous built-in CronJob Kubernetes type.
  *
 * @see https://book.kubebuilder.io/cronjob-tutorial/cronjob-tutorial
 */
@experimental
@customResource(
  group = "batch.tutorial.skuber.io",
  version = "v1",
  kind = "KronJob",
  scope = Scope.Namespaced,
  statusSubresource = true
)
object KronJobResource extends CustomResourceDef[KronJobResource.Spec, KronJobResource.Status]:

  /**
   * ConcurrencyPolicy determines how the controller handles concurrent job executions.
   */
  enum ConcurrencyPolicy:
    /** Allow concurrent job executions (default) */
    case Allow
    /** Forbid concurrent executions - skip new run if previous still running */
    case Forbid
    /** Replace - cancel currently running job and start a new one */
    case Replace

  /**
   * JobTemplateSpec defines the job to be created when the schedule fires.
   * Simplified version - in production you'd include the full Pod.Template.Spec.
   */
  case class JobTemplateSpec(
    /** Container image to run */
    image: String,
    /** Command to execute */
    command: List[String] = Nil,
    /** Arguments to the command */
    args: List[String] = Nil,
    /** Restart policy for the job's pods */
    restartPolicy: String = "OnFailure"
  )

  /**
   * CronJobSpec defines the desired state of the CronJob.
   */
  case class Spec(
    /** Schedule in Cron format, e.g. "0 * * * *" for hourly */
    schedule: String,

    /** Job template to create when the schedule fires */
    jobTemplate: JobTemplateSpec,

    /** Deadline in seconds for starting the job if it misses scheduled time.
     *  If not set, no deadline is enforced. */
    startingDeadlineSeconds: Option[Long] = None,

    /** How to handle concurrent job executions */
    concurrencyPolicy: ConcurrencyPolicy = ConcurrencyPolicy.Allow,

    /** If true, subsequent executions are suspended */
    suspend: Boolean = false,

    /** Number of successful jobs to retain (default 3) */
    successfulJobsHistoryLimit: Int = 3,

    /** Number of failed jobs to retain (default 1) */
    failedJobsHistoryLimit: Int = 1
  )

  /**
   * CronJobStatus defines the observed state of the CronJob.
   */
  case class Status(
    /** References to currently running jobs */
    active: List[ObjectReference] = Nil,

    /** Last time a job was successfully scheduled */
    lastScheduleTime: Option[ZonedDateTime] = None,

    /** Last time the CronJob was successfully reconciled */
    lastSuccessfulTime: Option[ZonedDateTime] = None
  )

/** Type alias for cleaner API usage */
type KronJob = KronJobResource.Resource
