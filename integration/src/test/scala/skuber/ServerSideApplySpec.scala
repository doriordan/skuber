package skuber

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import skuber.api.client.{ApplyOptions, DeleteOptions, DeletePropagation, K8SException}
import skuber.model.LabelSelector
import skuber.model.apps.v1.Deployment
import skuber.model.ac.apps.v1.{DeploymentApplyConfig, DeploymentSpecApplyConfig}
import skuber.model.ac.{ContainerApplyConfig, PodTemplateSpecApplyConfig, PodSpecApplyConfig}
import skuber.json.format._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Shared integration tests for server-side apply operations that work with the Pekko client.
 * The concrete fixture (PekkoK8SFixture) is mixed in via build configuration.
 */
abstract class ServerSideApplySpec extends K8SFixture with Eventually with Matchers {

  val deploymentName: String = java.util.UUID.randomUUID().toString

  behavior of "Server-side apply"

  it should "create and modify a deployment via apply" in {
    withK8sClient(test = { k8s =>
      val fieldManager = "skuber-ssa-test"

      val initialConfig = DeploymentApplyConfig(deploymentName)
        .addLabel("app" -> deploymentName)
        .withSpec(DeploymentSpecApplyConfig()
          .withReplicas(1)
          .withSelector(LabelSelector(LabelSelector.IsEqualRequirement("app", deploymentName)))
          .withTemplate(PodTemplateSpecApplyConfig()
            .addLabel("app" -> deploymentName)
            .withPodSpec(PodSpecApplyConfig()
              .addContainer(ContainerApplyConfig("nginx", "nginx:1.25").exposePort(80))
            )
          )
        )

      for {
        // Step 1: Apply config to CREATE the deployment
        created <- k8s.apply[Deployment, DeploymentApplyConfig](initialConfig, ApplyOptions(fieldManager = fieldManager))

        // Step 2: Verify it exists and has the expected replica count
        _ = created.name shouldBe deploymentName
        _ = created.spec.flatMap(_.replicas) shouldBe Some(1)

        // Step 3: Wait for the deployment to become available (availableReplicas >= 1)
        _ = eventually(timeout(120.seconds), interval(3.seconds)) {
          val d = Await.result(k8s.get[Deployment](deploymentName), 5.seconds)
          d.status.map(_.availableReplicas).getOrElse(0) should be >= 1
        }

        // Step 4: Apply a modified config to UPDATE via SSA (2 replicas, nginx:1.27)
        updatedConfig = DeploymentApplyConfig(deploymentName)
          .addLabel("app" -> deploymentName)
          .withSpec(DeploymentSpecApplyConfig()
            .withReplicas(2)
            .withSelector(LabelSelector(LabelSelector.IsEqualRequirement("app", deploymentName)))
            .withTemplate(PodTemplateSpecApplyConfig()
              .addLabel("app" -> deploymentName)
              .withPodSpec(PodSpecApplyConfig()
                .addContainer(ContainerApplyConfig("nginx", "nginx:1.27").exposePort(80))
              )
            )
          )

        updated <- k8s.apply[Deployment, DeploymentApplyConfig](updatedConfig, ApplyOptions(fieldManager = fieldManager))

        // Step 5: Verify updated state (replicas=2, image=nginx:1.27)
        _ = updated.spec.flatMap(_.replicas) shouldBe Some(2)
        _ = updated.spec
              .flatMap(_.template.spec)
              .flatMap(_.containers.headOption)
              .map(_.image) shouldBe Some("nginx:1.27")

        // Step 6: Wait for the updated deployment to reach desired state
        _ = eventually(timeout(120.seconds), interval(3.seconds)) {
          val d = Await.result(k8s.get[Deployment](deploymentName), 5.seconds)
          d.status.map(_.availableReplicas).getOrElse(0) should be >= 2
        }

        // Step 7: Clean up: delete the deployment
        _ <- k8s.deleteWithOptions[Deployment](deploymentName, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))

        // Wait for deletion to complete
        result = eventually(timeout(120.seconds), interval(3.seconds)) {
          val attempt = Await.ready(k8s.get[Deployment](deploymentName), 5.seconds).value.get
          attempt match {
            case Success(_) => fail("Deleted deployment still exists")
            case Failure(ex) => ex match {
              case ex: K8SException if ex.status.code.contains(404) => succeed
              case _ => fail(s"Unexpected exception: ${ex.getMessage}")
            }
          }
        }
      } yield result
    }, timeout = 600.seconds)
  }
}
