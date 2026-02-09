package skuber.operator.leaderelection

import org.apache.pekko.actor.{ActorSystem, Cancellable}
import play.api.libs.json.*
import skuber.api.client.{K8SException, RequestLoggingContext}
import skuber.json.format.configMapFmt
import skuber.model.{ConfigMap, ObjectMeta, ResourceDefinition}
import skuber.pekkoclient.PekkoKubernetesClient
import skuber.operator.reconciler.OperatorLogger

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/**
 * Leader election record stored in the ConfigMap.
 */
case class LeaderElectionRecord(
  holderIdentity: String,
  leaseDurationSeconds: Int,
  acquireTime: Instant,
  renewTime: Instant,
  leaderTransitions: Int
)

object LeaderElectionRecord:
  given Format[LeaderElectionRecord] = new Format[LeaderElectionRecord]:
    def reads(json: JsValue): JsResult[LeaderElectionRecord] =
      for
        holder <- (json \ "holderIdentity").validate[String]
        duration <- (json \ "leaseDurationSeconds").validate[Int]
        acquire <- (json \ "acquireTime").validate[String].map(Instant.parse)
        renew <- (json \ "renewTime").validate[String].map(Instant.parse)
        transitions <- (json \ "leaderTransitions").validate[Int]
      yield LeaderElectionRecord(holder, duration, acquire, renew, transitions)

    def writes(r: LeaderElectionRecord): JsValue = Json.obj(
      "holderIdentity" -> r.holderIdentity,
      "leaseDurationSeconds" -> r.leaseDurationSeconds,
      "acquireTime" -> r.acquireTime.toString,
      "renewTime" -> r.renewTime.toString,
      "leaderTransitions" -> r.leaderTransitions
    )

/**
 * Leader election using ConfigMap-based locks.
 *
 * This implementation follows the Kubernetes leader election pattern:
 * - A ConfigMap is used as a distributed lock
 * - The leader periodically renews its claim
 * - If the leader stops renewing, another instance can take over
 */
class LeaderElection(
  client: PekkoKubernetesClient,
  config: LeaderElectionConfig
)(using system: ActorSystem):

  given ExecutionContext = system.dispatcher
  given lc: RequestLoggingContext = RequestLoggingContext()

  private val log = OperatorLogger("skuber.operator.leaderelection")
  private val lockKey = "leader-election-record"

  @volatile private var isLeader = false
  @volatile private var running = false
  private var renewSchedule: Option[Cancellable] = None
  private val stopPromise = Promise[Unit]()

  /**
   * Run leader election.
   *
   * @param onStartedLeading Called when this instance becomes the leader
   * @param onStoppedLeading Called when this instance loses leadership
   * @return Future that completes when leader election is stopped
   */
  def run(
    onStartedLeading: () => Future[Unit],
    onStoppedLeading: () => Future[Unit]
  ): Future[Unit] =
    running = true
    log.info(s"Starting leader election for ${config.leaseName} with identity ${config.identity}")

    // Start the acquisition loop
    acquire(onStartedLeading, onStoppedLeading)

    stopPromise.future

  /**
   * Stop leader election and release leadership if held.
   */
  def stop(): Future[Unit] =
    log.info("Stopping leader election")
    running = false
    renewSchedule.foreach(_.cancel())

    if isLeader then
      releaseLeadership()
    else
      Future.successful(())

    stopPromise.trySuccess(())
    stopPromise.future

  private def acquire(
    onStartedLeading: () => Future[Unit],
    onStoppedLeading: () => Future[Unit]
  ): Unit =
    if !running then return

    tryAcquireOrRenew().onComplete {
      case Success(true) =>
        if !isLeader then
          log.info(s"Acquired leadership for ${config.leaseName}")
          isLeader = true
          onStartedLeading().onComplete {
            case Success(_) =>
              scheduleRenew(onStartedLeading, onStoppedLeading)
            case Failure(e) =>
              log.error("Failed to start leading", e)
              isLeader = false
          }
        else
          scheduleRenew(onStartedLeading, onStoppedLeading)

      case Success(false) =>
        if isLeader then
          log.warn(s"Lost leadership for ${config.leaseName}")
          isLeader = false
          onStoppedLeading()
        scheduleRetry(onStartedLeading, onStoppedLeading)

      case Failure(e) =>
        log.error("Error during leader election", e)
        if isLeader then
          isLeader = false
          onStoppedLeading()
        scheduleRetry(onStartedLeading, onStoppedLeading)
    }

  private def scheduleRenew(
    onStartedLeading: () => Future[Unit],
    onStoppedLeading: () => Future[Unit]
  ): Unit =
    if running then
      renewSchedule = Some(
        system.scheduler.scheduleOnce(config.retryPeriod) {
          acquire(onStartedLeading, onStoppedLeading)
        }
      )

  private def scheduleRetry(
    onStartedLeading: () => Future[Unit],
    onStoppedLeading: () => Future[Unit]
  ): Unit =
    if running then
      renewSchedule = Some(
        system.scheduler.scheduleOnce(config.retryPeriod) {
          acquire(onStartedLeading, onStoppedLeading)
        }
      )

  private def tryAcquireOrRenew(): Future[Boolean] =
    val k8s = client.usingNamespace(config.leaseNamespace)

    k8s.getOption[ConfigMap](config.leaseName).flatMap {
      case None =>
        // No lock exists, try to create it
        createLock().map(_ => true).recover {
          case _: K8SException => false // Another instance created it first
        }

      case Some(cm) =>
        // Lock exists, check if we can acquire or renew
        val recordJson = cm.data.get(lockKey).map(Json.parse)
        val record = recordJson.flatMap(_.asOpt[LeaderElectionRecord])

        record match
          case None =>
            // No valid record, try to acquire
            updateLock(cm, createRecord(0)).map(_ => true).recover {
              case _: K8SException => false
            }

          case Some(r) if r.holderIdentity == config.identity =>
            // We already hold the lock, renew it
            val renewed = r.copy(renewTime = Instant.now())
            updateLock(cm, renewed).map(_ => true).recover {
              case _: K8SException => false
            }

          case Some(r) =>
            // Someone else holds the lock, check if it's expired
            val expireTime = r.renewTime.plusSeconds(config.leaseDuration.toSeconds)
            if Instant.now().isAfter(expireTime) then
              // Lock expired, try to acquire
              val newRecord = createRecord(r.leaderTransitions + 1)
              updateLock(cm, newRecord).map(_ => true).recover {
                case _: K8SException => false
              }
            else
              // Lock is still valid
              Future.successful(false)
    }

  private def createLock(): Future[ConfigMap] =
    val k8s = client.usingNamespace(config.leaseNamespace)
    val record = createRecord(0)
    val cm = ConfigMap(
      metadata = ObjectMeta(
        name = config.leaseName,
        namespace = config.leaseNamespace
      ),
      data = Map(lockKey -> Json.stringify(Json.toJson(record)))
    )
    k8s.create(cm)

  private def updateLock(existing: ConfigMap, record: LeaderElectionRecord): Future[ConfigMap] =
    val k8s = client.usingNamespace(config.leaseNamespace)
    val updated = existing.copy(
      data = existing.data + (lockKey -> Json.stringify(Json.toJson(record)))
    )
    k8s.update(updated)

  private def createRecord(transitions: Int): LeaderElectionRecord =
    val now = Instant.now()
    LeaderElectionRecord(
      holderIdentity = config.identity,
      leaseDurationSeconds = config.leaseDuration.toSeconds.toInt,
      acquireTime = now,
      renewTime = now,
      leaderTransitions = transitions
    )

  private def releaseLeadership(): Future[Unit] =
    val k8s = client.usingNamespace(config.leaseNamespace)

    k8s.getOption[ConfigMap](config.leaseName).flatMap {
      case Some(cm) =>
        val recordJson = cm.data.get(lockKey).map(Json.parse)
        val record = recordJson.flatMap(_.asOpt[LeaderElectionRecord])

        record match
          case Some(r) if r.holderIdentity == config.identity =>
            // Release by setting renewTime far in the past
            val released = r.copy(renewTime = Instant.EPOCH)
            updateLock(cm, released).map(_ => ())
          case _ =>
            Future.successful(())

      case None =>
        Future.successful(())
    }.recover {
      case e =>
        log.warn(s"Failed to release leadership: ${e.getMessage}")
    }
