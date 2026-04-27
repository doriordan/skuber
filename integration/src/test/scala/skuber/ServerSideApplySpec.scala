package skuber

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import skuber.api.client.{ApplyOptions, DeleteOptions, DeletePropagation, K8SException}
import skuber.model.LabelSelector
import skuber.model.apps.v1.Deployment
import skuber.model.ac.apps.v1.{DeploymentApplyConfig, DeploymentSpecApplyConfig}
import skuber.model.ac.{ContainerApplyConfig, PodTemplateSpecApplyConfig, PodSpecApplyConfig}
import skuber.json.format._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

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

      def cleanup(): Future[Unit] =
        k8s.deleteWithOptions[Deployment](deploymentName, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))
          .recover { case _ => () }
          .flatMap { _ =>
            Future {
              eventually(timeout(120.seconds), interval(3.seconds)) {
                val attempt = Await.ready(k8s.get[Deployment](deploymentName), 5.seconds).value.get
                attempt match {
                  case Failure(ex: K8SException) if ex.status.code.contains(404) => ()
                  case _ => throw new AssertionError("Deployment still exists after deletion")
                }
              }
            }
          }
          .recover { case _ => () }

      val testResult = for {
        created <- k8s.apply[Deployment, DeploymentApplyConfig](initialConfig, ApplyOptions(fieldManager = fieldManager))
        _ = created.name shouldBe deploymentName
        _ = created.spec.flatMap(_.replicas) shouldBe Some(1)

        _ = eventually(timeout(200.seconds), interval(5.seconds)) {
          val d = Await.result(k8s.get[Deployment](deploymentName), 5.seconds)
          d.status.map(_.availableReplicas).getOrElse(0) should be >= 1
        }

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
        _ = updated.spec.flatMap(_.replicas) shouldBe Some(2)
        _ = updated.spec
              .flatMap(_.template.spec)
              .flatMap(_.containers.headOption)
              .map(_.image) shouldBe Some("nginx:1.27")

        _ = eventually(timeout(200.seconds), interval(5.seconds)) {
          val d = Await.result(k8s.get[Deployment](deploymentName), 5.seconds)
          d.status.map(_.availableReplicas).getOrElse(0) should be >= 2
        }
      } yield succeed

      testResult.transformWith { result =>
        cleanup().flatMap { _ =>
          result match {
            case Success(a) => Future.successful(a)
            case Failure(e) => Future.failed(e)
          }
        }
      }
    }, timeout = 600.seconds)
  }
}
