package skuber.examples.kronjob

import org.apache.pekko.actor.ActorSystem
import play.api.libs.json.Format
import skuber.api.client.RequestLoggingContext
import skuber.json.batch.format.{jobFormat, jobListFmt}
import skuber.json.format.ListResourceFormat
import skuber.model.{Container, ListResource, ObjectMeta, ObjectReference, OwnerReference, Pod, ResourceDefinition, RestartPolicy}
import skuber.model.batch.{Job, JobTemplate}
import skuber.operator.reconciler.*

import java.time.{Instant, ZoneId, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

/**
 * CronJobReconciler implements the reconciliation logic for CronJob resources.
 *
 * This follows the kubebuilder tutorial pattern:
 * 1. Load the CronJob
 * 2. List all owned Jobs and categorize them
 * 3. Clean up old Jobs based on history limits
 * 4. Check if suspended
 * 5. Get the next scheduled run time
 * 6. Create a new Job if it's time
 * 7. Requeue at the right time
 *
 * @see https://book.kubebuilder.io/cronjob-tutorial/controller-implementation
 */
class KronJobReconciler(using system: ActorSystem) extends Reconciler[KronJob]:

  import KronJobResource.given

  given ExecutionContext = system.dispatcher
  given RequestLoggingContext = RequestLoggingContext()

  /** Annotation key for the scheduled time on created Jobs */
  private val ScheduledTimeAnnotation = "batch.tutorial.skuber.io/scheduled-at"

  override def reconcile(resource: KronJob, ctx: ReconcileContext[KronJob]): Future[ReconcileResult] =
    val log = ctx.log
    val cronJob = resource

    log.info(s"Reconciling CronJob ${cronJob.name}")

    // Parse the cron schedule
    CronSchedule.parse(cronJob.spec.schedule) match
      case Left(error) =>
        log.error(s"Invalid cron schedule: $error")
        Future.successful(ReconcileResult.Done)

      case Right(schedule) =>
        reconcileWithSchedule(cronJob, schedule, ctx)

  private def reconcileWithSchedule(
    cronJob: KronJob,
    schedule: KronSchedule,
    ctx: ReconcileContext[KronJob]
  ): Future[ReconcileResult] =
    val log = ctx.log
    val now = ZonedDateTime.now()

    for
      // Step 2: List all child Jobs
      allJobs <- listChildJobs(cronJob, ctx)
      _ = log.debug(s"Found ${allJobs.size} child jobs")

      // Categorize jobs
      (activeJobs, successfulJobs, failedJobs) = categorizeJobs(allJobs)
      _ = log.info(s"Job status: ${activeJobs.size} active, ${successfulJobs.size} successful, ${failedJobs.size} failed")

      // Step 3: Clean up old jobs based on history limits
      _ <- cleanupOldJobs(cronJob, successfulJobs, failedJobs, ctx)

      // Update status with current active jobs
      _ <- updateStatus(cronJob, activeJobs, ctx)

      // Step 4: Check if suspended
      result <- if cronJob.spec.suspend then
        log.info("CronJob is suspended, skipping")
        Future.successful(ReconcileResult.Done)
      else
        // Step 5 & 6: Check schedule and create job if needed
        handleSchedule(cronJob, schedule, activeJobs, now, ctx)

    yield result

  /**
   * List all Jobs owned by this CronJob.
   */
  private def listChildJobs(cronJob: KronJob, ctx: ReconcileContext[KronJob]): Future[List[Job]] =
    val ns = cronJob.metadata.namespace
    ctx.client.usingNamespace(ns).list[ListResource[Job]]().map { jobList =>
      jobList.items.filter { job =>
        job.metadata.ownerReferences.exists { ref =>
          ref.kind == "CronJob" &&
          ref.name == cronJob.name &&
          ref.uid == cronJob.metadata.uid
        }
      }
    }

  /**
   * Categorize jobs into active, successful, and failed.
   */
  private def categorizeJobs(jobs: List[Job]): (List[Job], List[Job], List[Job]) =
    val active = jobs.filter(isJobActive)
    val successful = jobs.filter(isJobSucceeded)
    val failed = jobs.filter(isJobFailed)
    (active, successful, failed)

  private def isJobActive(job: Job): Boolean =
    job.status.flatMap(_.active).getOrElse(0) > 0

  private def isJobSucceeded(job: Job): Boolean =
    job.status.flatMap(_.succeeded).getOrElse(0) > 0

  private def isJobFailed(job: Job): Boolean =
    job.status.flatMap(_.failed).getOrElse(0) > 0

  /**
   * Clean up old jobs based on history limits.
   */
  private def cleanupOldJobs(
    kronJob: KronJob,
    successfulJobs: List[Job],
    failedJobs: List[Job],
    ctx: ReconcileContext[KronJob]
  ): Future[Unit] =
    val log = ctx.log

    def sortByStartTime(jobs: List[Job]): List[Job] =
      jobs.sortBy(_.status.flatMap(_.startTime).map(_.toInstant.toEpochMilli).getOrElse(0L))

    def deleteOldest(jobs: List[Job], limit: Int, jobType: String): Future[Unit] =
      val sorted = sortByStartTime(jobs)
      val toDelete = sorted.dropRight(limit)
      Future.traverse(toDelete) { job =>
        log.info(s"Deleting old $jobType job: ${job.name}")
        ctx.client.usingNamespace(kronJob.metadata.namespace).delete[Job](job.name)
          .recover { case e => log.warn(s"Failed to delete job ${job.name}: ${e.getMessage}") }
      }.map(_ => ())

    for
      _ <- deleteOldest(successfulJobs, kronJob.spec.successfulJobsHistoryLimit, "successful")
      _ <- deleteOldest(failedJobs, kronJob.spec.failedJobsHistoryLimit, "failed")
    yield ()

  /**
   * Update the CronJob status with active job references.
   */
  private def updateStatus(
    kronJob: KronJob,
    activeJobs: List[Job],
    ctx: ReconcileContext[KronJob]
  ): Future[Unit] =
    val activeRefs = activeJobs.map { job =>
      ObjectReference(
        kind = "Job",
        name = job.name,
        namespace = job.metadata.namespace,
        uid = job.metadata.uid
      )
    }

    val currentStatus = kronJob.status.getOrElse(KronJobResource.Status())
    val newStatus = currentStatus.copy(active = activeRefs)

    if kronJob.status.map(_.active) != Some(activeRefs) then
      val updated = kronJob.copy(status = Some(newStatus))
      ctx.client.usingNamespace(kronJob.metadata.namespace).updateStatus(updated).map(_ => ())
    else
      Future.successful(())

  /**
   * Handle scheduling - create a new job if it's time.
   */
  private def handleSchedule(
    kronJob: KronJob,
    schedule: KronSchedule,
    activeJobs: List[Job],
    now: ZonedDateTime,
    ctx: ReconcileContext[KronJob]
  ): Future[ReconcileResult] =
    val log = ctx.log

    // Get the last scheduled time from status
    val lastScheduleTime = kronJob.status.flatMap(_.lastScheduleTime)

    // Find the most recent time we should have run
    val scheduledTime = schedule.mostRecentBefore(now)

    scheduledTime match
      case None =>
        log.info("No scheduled time found")
        val nextRun = schedule.nextAfter(now)
        val delay = java.time.Duration.between(now, nextRun).toMillis.millis
        Future.successful(ReconcileResult.RequeueAfter(delay, s"Next run at $nextRun"))

      case Some(scheduled) =>
        // Check if we already ran for this scheduled time
        val alreadyRan = lastScheduleTime.exists(!_.isBefore(scheduled))

        if alreadyRan then
          log.debug(s"Already ran for scheduled time $scheduled")
          val nextRun = schedule.nextAfter(now)
          val delay = java.time.Duration.between(now, nextRun).toMillis.millis
          Future.successful(ReconcileResult.RequeueAfter(delay, s"Next run at $nextRun"))
        else
          // Check starting deadline
          val missedDeadline = kronJob.spec.startingDeadlineSeconds.exists { deadline =>
            java.time.Duration.between(scheduled, now).getSeconds > deadline
          }

          if missedDeadline then
            log.warn(s"Missed starting deadline for scheduled time $scheduled")
            val nextRun = schedule.nextAfter(now)
            val delay = java.time.Duration.between(now, nextRun).toMillis.millis
            Future.successful(ReconcileResult.RequeueAfter(delay, s"Missed deadline, next run at $nextRun"))
          else
            // Handle concurrency policy
            handleConcurrency(kronJob, scheduled, activeJobs, schedule, now, ctx)

  /**
   * Handle concurrency policy and create job if appropriate.
   */
  private def handleConcurrency(
    kronJob: KronJob,
    scheduledTime: ZonedDateTime,
    activeJobs: List[Job],
    schedule: KronSchedule,
    now: ZonedDateTime,
    ctx: ReconcileContext[KronJob]
  ): Future[ReconcileResult] =
    import KronJobResource.ConcurrencyPolicy.*
    val log = ctx.log

    kronJob.spec.concurrencyPolicy match
      case Forbid if activeJobs.nonEmpty =>
        log.info(s"Skipping scheduled run - ${activeJobs.size} jobs still active (Forbid policy)")
        val nextRun = schedule.nextAfter(now)
        val delay = java.time.Duration.between(now, nextRun).toMillis.millis
        Future.successful(ReconcileResult.RequeueAfter(delay, "Active jobs running"))

      case Replace if activeJobs.nonEmpty =>
        log.info(s"Cancelling ${activeJobs.size} active jobs (Replace policy)")
        for
          _ <- Future.traverse(activeJobs) { job =>
            ctx.client.usingNamespace(kronJob.metadata.namespace).delete[Job](job.name)
              .recover { case e => log.warn(s"Failed to delete job ${job.name}: ${e.getMessage}") }
          }
          result <- createJob(kronJob, scheduledTime, schedule, now, ctx)
        yield result

      case _ => // Allow or no active jobs
        createJob(kronJob, scheduledTime, schedule, now, ctx)

  /**
   * Create a new Job for the scheduled time.
   */
  private def createJob(
    cronJob: KronJob,
    scheduledTime: ZonedDateTime,
    schedule: KronSchedule,
    now: ZonedDateTime,
    ctx: ReconcileContext[KronJob]
  ): Future[ReconcileResult] =
    val log = ctx.log

    // Generate deterministic job name
    val jobName = s"${cronJob.name}-${scheduledTime.toInstant.getEpochSecond}"

    log.info(s"Creating job $jobName for scheduled time $scheduledTime")

    val job = buildJob(cronJob, jobName, scheduledTime)

    // Create job, handling AlreadyExists gracefully (job may have been created in a previous reconcile)
    val createFuture = ctx.client.usingNamespace(cronJob.metadata.namespace).create(job).map { _ =>
      log.info(s"Created job $jobName")
    }.recover {
      case e if e.getMessage.contains("AlreadyExists") || e.getMessage.contains("409") =>
        log.debug(s"Job $jobName already exists, continuing")
    }

    for
      _ <- createFuture

      // Update last schedule time
      currentStatus = cronJob.status.getOrElse(KronJobResource.Status())
      newStatus = currentStatus.copy(lastScheduleTime = Some(scheduledTime))
      updated = cronJob.copy(status = Some(newStatus))
      _ <- ctx.client.usingNamespace(cronJob.metadata.namespace).updateStatus(updated)

      // Calculate next run time
      nextRun = schedule.nextAfter(now)
      delay = java.time.Duration.between(now, nextRun).toMillis.millis
    yield
      log.info(s"Job $jobName scheduled, next run at $nextRun")
      ReconcileResult.RequeueAfter(delay, s"Next run at $nextRun")

  /**
   * Build a Job from the KronJob template.
   */
  private def buildJob(kronJob: KronJob, jobName: String, scheduledTime: ZonedDateTime): Job =
    val template = kronJob.spec.jobTemplate

    val container = Container(
      name = "job",
      image = template.image,
      command = if template.command.isEmpty then Nil else template.command,
      args = if template.args.isEmpty then Nil else template.args
    )

    val podSpec = Pod.Spec(
      containers = List(container),
      restartPolicy = RestartPolicy.withName(template.restartPolicy)
    )

    val podTemplate = Pod.Template.Spec(
      metadata = ObjectMeta(),
      spec = Some(podSpec)
    )

    val jobSpec = Job.Spec(
      template = Some(podTemplate),
      backoffLimit = Some(2)
    )

    Job(
      metadata = ObjectMeta(
        name = jobName,
        namespace = kronJob.metadata.namespace,
        annotations = Map(ScheduledTimeAnnotation -> scheduledTime.toString),
        ownerReferences = List(OwnerReference(
          apiVersion = kronJob.apiVersion,
          kind = kronJob.kind,
          name = kronJob.name,
          uid = kronJob.metadata.uid,
          controller = Some(true),
          blockOwnerDeletion = Some(true)
        ))
      ),
      spec = Some(jobSpec)
    )
