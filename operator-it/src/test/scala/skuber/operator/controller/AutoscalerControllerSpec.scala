package skuber.operator.controller

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.time.SpanSugar.*
import play.api.libs.json.Format
import skuber.api.client.RequestLoggingContext
import skuber.model.{ObjectResource, ResourceDefinition}
import skuber.model.apiextensions.v1.CustomResourceDefinition
import skuber.operator.crd.{Autoscaler, AutoscalerCRDFixture, PekkoK8SFixture}
import skuber.operator.reconciler.*
import skuber.pekkoclient.*

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.*

/**
 * Integration test for the operator controller framework using the Autoscaler CRD.
 *
 * This test verifies that:
 * 1. A controller can be started and watch resources
 * 2. The reconciler is called when resources change
 * 3. Status updates trigger re-reconciliation
 * 4. The controller properly tracks reconciliation until desired state is reached
 */
class AutoscalerControllerSpec extends AsyncFlatSpec with PekkoK8SFixture with Eventually with Matchers with BeforeAndAfterAll {

  import Autoscaler.given

  given lc: RequestLoggingContext = RequestLoggingContext()

  val testResourceName = "controller-test-autoscaler"
  val desiredReplicas = 10

  override def beforeAll(): Unit = {
    super.beforeAll()
    val k8s = createK8sClient(config)
    try {
      // Clean up any existing CRD from previous test runs
      try {
        Await.ready(k8s.delete[CustomResourceDefinition](AutoscalerCRDFixture.crdName), 10.seconds)
        Thread.sleep(2000)
      } catch {
        case _: Exception => // CRD doesn't exist
      }

      // Create the CRD
      Await.ready(k8s.create(AutoscalerCRDFixture.crd), 10.seconds)
      Thread.sleep(3000) // Wait for CRD to be established
    } finally {
      k8s.close()
    }
  }

  override def afterAll(): Unit = {
    val k8s = createK8sClient(config)
    try {
      try {
        Await.ready(k8s.delete[Autoscaler.Resource](testResourceName), 5.seconds)
      } catch {
        case _: Exception =>
      }
      try {
        Await.ready(k8s.delete[CustomResourceDefinition](AutoscalerCRDFixture.crdName), 10.seconds)
      } catch {
        case _: Exception =>
      }
    } finally {
      k8s.close()
    }
    super.afterAll()
  }

  behavior of "AutoscalerController"

  it should "reconcile Autoscaler until actual replicas equals desired replicas" in {
    implicit val actorSystem: ActorSystem = system
    given Materializer = Materializer(actorSystem)

    val k8s = k8sInit(config)

    // Track reconciliation events
    val reconcileCount = new AtomicInteger(0)
    val reconcileLog = ListBuffer.empty[String]
    val reachedDesiredState = new AtomicBoolean(false)
    val reconciliationComplete = Promise[Unit]()
    val stopStatusUpdater = new AtomicBoolean(false)

    // Create the reconciler
    val reconciler = new Reconciler[Autoscaler.Resource] {
      def reconcile(resource: Autoscaler.Resource, ctx: ReconcileContext[Autoscaler.Resource]): Future[ReconcileResult] = {
        val count = reconcileCount.incrementAndGet()
        val currentReplicas = resource.status.map(_.availableReplicas).getOrElse(0)
        val desired = resource.spec.desiredReplicas

        val msg = s"Reconcile #$count: ${resource.name} - current=$currentReplicas, desired=$desired"
        reconcileLog.synchronized {
          reconcileLog += msg
        }
        println(msg)

        if (currentReplicas >= desired) {
          reachedDesiredState.set(true)
          reconciliationComplete.trySuccess(())
          Future.successful(ReconcileResult.Done)
        } else {
          // Still waiting for status to be updated - requeue to check again
          Future.successful(ReconcileResult.RequeueAfter(1.second, s"Waiting for replicas: $currentReplicas/$desired"))
        }
      }
    }

    // Create the controller manager and controller
    val managerConfig = ManagerConfig(
      namespace = Some("default"),
      leaderElection = None,
      cacheSyncTimeout = 30.seconds
    )

    val manager = ControllerManager(managerConfig, k8s)

    val controller = ControllerBuilder[Autoscaler.Resource](manager)
      .withReconciler(reconciler)
      .withConcurrency(1)
      .build()

    manager.add(controller)

    // Background task to simulate status updates
    // Increment availableReplicas by 1 every 2 seconds until it equals desiredReplicas
    def runStatusUpdater(): Future[Unit] = Future {
      var currentReplicas = 0
      while (currentReplicas < desiredReplicas && !reachedDesiredState.get() && !stopStatusUpdater.get()) {
        Thread.sleep(2000)

        if (!stopStatusUpdater.get()) {
          try {
            // Get latest version of resource
            val latest = Await.result(k8s.get[Autoscaler.Resource](testResourceName), 5.seconds)
            currentReplicas = latest.status.map(_.availableReplicas).getOrElse(0) + 1
            val ready = currentReplicas >= desiredReplicas

            val newStatus = Autoscaler.Status(availableReplicas = currentReplicas, ready = ready)
            val updated = latest.copy(status = Some(newStatus))

            println(s"Status updater: Setting availableReplicas=$currentReplicas, ready=$ready")
            Await.result(k8s.updateStatus(updated), 5.seconds)
          } catch {
            case e: Exception if stopStatusUpdater.get() =>
              // Ignore errors during shutdown
              println(s"Status updater: Ignoring error during shutdown: ${e.getMessage}")
            case e: Exception =>
              println(s"Status updater: Error updating status: ${e.getMessage}")
              throw e
          }
        }
      }
      println("Status updater: Completed")
    }

    val testResult = for {
      // Start the manager
      _ <- manager.start()
      _ = println("Controller manager started")

      // Create the Autoscaler resource with desired replicas = 10
      autoscaler = Autoscaler(testResourceName, Autoscaler.Spec(desiredReplicas, "nginx:latest"))
      created <- k8s.create(autoscaler)
      _ = println(s"Created Autoscaler: ${created.name} with desiredReplicas=${created.spec.desiredReplicas}")

      // Start the status updater in background
      statusUpdaterFuture = runStatusUpdater()

      // Wait for reconciliation to complete (with timeout)
      _ <- Future {
        try {
          Await.result(reconciliationComplete.future, 90.seconds)
        } catch {
          case e: java.util.concurrent.TimeoutException =>
            stopStatusUpdater.set(true)
            throw new RuntimeException(s"Timed out waiting for reconciliation. Log: ${reconcileLog.mkString(", ")}")
        }
      }
      _ = println(s"Reconciliation complete after ${reconcileCount.get()} reconcile calls")
      _ = println("Reconcile log:")
      _ = reconcileLog.foreach(println)

      // Signal status updater to stop and wait for it
      _ = stopStatusUpdater.set(true)
      _ <- statusUpdaterFuture.recover { case _ => () }

      // Stop the manager
      _ <- manager.stop()
      _ = println("Controller manager stopped")
    } yield {
      // Verify we reached the desired state
      reachedDesiredState.get() shouldBe true
      reconcileCount.get() should be >= 1
      succeed
    }

    testResult.transform { result =>
      k8s.close()
      result
    }
  }
}
