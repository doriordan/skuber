package skuber.examples.kronjob

import org.apache.pekko.actor.ActorSystem
import skuber.api.client.RequestLoggingContext
import skuber.model.batch.Job
import skuber.operator.controller.{ControllerBuilder, ControllerManager, ManagerConfig}
import skuber.pekkoclient.k8sInit

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/**
 * KronJob Operator - Example Kubernetes operator built with Skuber.
 *
 * This operator demonstrates the controller pattern following the
 * kubebuilder tutorial: https://book.kubebuilder.io/cronjob-tutorial/cronjob-tutorial
 *
 * Features demonstrated:
 * - Custom Resource Definition (CRD) using @customResource macro
 * - Reconciler pattern for declarative resource management
 * - Managing child resources (Jobs) with owner references
 * - Cron schedule parsing and execution
 * - Status updates and job cleanup
 *
 * Prerequisites:
 * - A running Kubernetes cluster (minikube, kind, or remote)
 * - Valid kubeconfig
 * - The KronJob CRD must be installed (see KronJobCRDInstaller)
 *
 * Usage:
 *   sbt "operator-ex/run"
 */
object KronJobOperator:

  import KronJobResource.given
  import skuber.json.batch.format.jobFormat

  def main(args: Array[String]): Unit =
    given system: ActorSystem = ActorSystem("kronjob-operator")
    given ExecutionContext = system.dispatcher
    given RequestLoggingContext = RequestLoggingContext()

    println("=" * 60)
    println("Skuber KronJob Operator Example")
    println("=" * 60)

    val k8s = k8sInit

    // Configure the controller manager
    val managerConfig = ManagerConfig(
      namespace = None,  // Watch all namespaces
      leaderElection = None,  // Disable leader election for this example
      cacheSyncTimeout = 30.seconds
    )

    val manager = ControllerManager(managerConfig, k8s)

    // Create the reconciler
    val reconciler = new KronJobReconciler()

    // Build the controller
    val controller = ControllerBuilder[KronJob](manager)
      .withReconciler(reconciler)
      .owns[Job]  // Watch Jobs owned by CronJobs
      .withConcurrency(2)
      .build()

    manager.add(controller)

    println("Starting controller manager...")

    // Start the manager
    val startFuture = manager.start()

    startFuture.onComplete {
      case Success(_) =>
        println("Controller manager started successfully")
        println("Watching for KronJob resources...")
        println("Press Ctrl+C to stop")

      case Failure(e) =>
        println(s"Failed to start controller manager: ${e.getMessage}")
        e.printStackTrace()
        system.terminate()
    }

    // Handle shutdown
    sys.addShutdownHook {
      println("\nShutting down...")
      Await.result(manager.stop(), 30.seconds)
      k8s.close()
      Await.result(system.terminate(), 5.seconds)
      println("Shutdown complete")
    }

    // Keep the main thread alive
    Await.result(system.whenTerminated, Duration.Inf)

/**
 * Utility to install the CronJob CRD.
 *
 * Run this before starting the operator:
 *   sbt "operator-ex/runMain skuber.examples.kronjob.KronJobCRDInstaller"
 */
object KronJobCRDInstaller:

  import skuber.model.apiextensions.v1.CustomResourceDefinition.crdFmt

  def main(args: Array[String]): Unit =
    given system: ActorSystem = ActorSystem("crd-installer")
    given ExecutionContext = system.dispatcher
    given RequestLoggingContext = RequestLoggingContext()

    val k8s = k8sInit

    println("Installing KronJob CRD...")

    val crd = buildCRD()

    val result = k8s.create(crd).recover {
      case e if e.getMessage.contains("409") || e.getMessage.contains("AlreadyExists") =>
        println("CRD already exists, updating...")
        // For update, we'd need to get first and update
        crd
    }

    result.onComplete {
      case Success(_) =>
        println("CRD installed successfully!")
        println(s"  Group: batch.tutorial.skuber.io")
        println(s"  Kind: KronJob")
        println(s"  Version: v1")
        k8s.close()
        system.terminate()

      case Failure(e) =>
        println(s"Failed to install CRD: ${e.getMessage}")
        e.printStackTrace()
        k8s.close()
        system.terminate()
    }

    Await.result(system.whenTerminated, 30.seconds)

  private def buildCRD(): skuber.model.apiextensions.v1.CustomResourceDefinition =
    import skuber.model.ObjectMeta
    import skuber.model.ResourceSpecification
    import skuber.model.ResourceSpecification.{Names, Schema, Scope}
    import skuber.model.NonCoreResourceSpecification
    import skuber.model.apiextensions.v1.CustomResourceDefinition
    import play.api.libs.json.Json

    val schema = Schema(Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(
        "spec" -> Json.obj(
          "type" -> "object",
          "required" -> Json.arr("schedule", "jobTemplate"),
          "properties" -> Json.obj(
            "schedule" -> Json.obj("type" -> "string"),
            "jobTemplate" -> Json.obj(
              "type" -> "object",
              "required" -> Json.arr("image"),
              "properties" -> Json.obj(
                "image" -> Json.obj("type" -> "string"),
                "command" -> Json.obj(
                  "type" -> "array",
                  "items" -> Json.obj("type" -> "string")
                ),
                "args" -> Json.obj(
                  "type" -> "array",
                  "items" -> Json.obj("type" -> "string")
                ),
                "restartPolicy" -> Json.obj(
                  "type" -> "string",
                  "enum" -> Json.arr("Always", "OnFailure", "Never")
                )
              )
            ),
            "startingDeadlineSeconds" -> Json.obj("type" -> "integer"),
            "concurrencyPolicy" -> Json.obj(
              "type" -> "string",
              "enum" -> Json.arr("Allow", "Forbid", "Replace")
            ),
            "suspend" -> Json.obj("type" -> "boolean"),
            "successfulJobsHistoryLimit" -> Json.obj("type" -> "integer"),
            "failedJobsHistoryLimit" -> Json.obj("type" -> "integer")
          )
        ),
        "status" -> Json.obj(
          "type" -> "object",
          "properties" -> Json.obj(
            "active" -> Json.obj(
              "type" -> "array",
              "items" -> Json.obj(
                "type" -> "object",
                "properties" -> Json.obj(
                  "kind" -> Json.obj("type" -> "string"),
                  "name" -> Json.obj("type" -> "string"),
                  "namespace" -> Json.obj("type" -> "string"),
                  "uid" -> Json.obj("type" -> "string")
                )
              )
            ),
            "lastScheduleTime" -> Json.obj("type" -> "string", "format" -> "date-time"),
            "lastSuccessfulTime" -> Json.obj("type" -> "string", "format" -> "date-time")
          )
        )
      )
    ))

    CustomResourceDefinition(
      metadata = ObjectMeta(name = "kronjobs.batch.tutorial.skuber.io"),
      spec = NonCoreResourceSpecification(
        apiGroup = "batch.tutorial.skuber.io",
        version = Some("v1"),
        scope = Scope.Namespaced,
        names = Names(
          plural = "kronjobs",
          singular = "kronjob",
          kind = "KronJob",
          shortNames = List("kj")
        ),
        versions = Some(List(
          ResourceSpecification.Version(
            name = "v1",
            served = true,
            storage = true,
            schema = Some(schema),
            subresources = Some(ResourceSpecification.Subresources(
              status = Some(ResourceSpecification.StatusSubresource())
            ))
          )
        )),
        subresources = None
      )
    )
