package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import skuber.api.client.{ApplyOptions, DeleteOptions, DeletePropagation, LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.LabelSelector
import skuber.model.LabelSelector.dsl.*
import skuber.model.ac.apps.v1.{DeploymentApplyConfig, DeploymentSpecApplyConfig}
import skuber.model.ac.{ContainerApplyConfig, PodSpecApplyConfig, PodTemplateSpecApplyConfig}
import skuber.model.apps.v1.Deployment
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*

class CatsServerSideApplySpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 10.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  client.test("server-side apply creates and updates a deployment") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    val fieldManager = "skuber-ssa-test"

    val initialConfig = DeploymentApplyConfig(name)
      .addLabel("app" -> name)
      .withSpec(DeploymentSpecApplyConfig()
        .withReplicas(1)
        .withSelector(LabelSelector(LabelSelector.IsEqualRequirement("app", name)))
        .withTemplate(PodTemplateSpecApplyConfig()
          .addLabel("app" -> name)
          .withPodSpec(PodSpecApplyConfig()
            .addContainer(ContainerApplyConfig("nginx", "nginx:1.25").exposePort(80))
          )
        )
      )

    val updatedConfig = DeploymentApplyConfig(name)
      .addLabel("app" -> name)
      .withSpec(DeploymentSpecApplyConfig()
        .withReplicas(2)
        .withSelector(LabelSelector(LabelSelector.IsEqualRequirement("app", name)))
        .withTemplate(PodTemplateSpecApplyConfig()
          .addLabel("app" -> name)
          .withPodSpec(PodSpecApplyConfig()
            .addContainer(ContainerApplyConfig("nginx", "nginx:1.27").exposePort(80))
          )
        )
      )

    val cleanup =
      k8s.deleteWithOptions[Deployment](name, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))
        .flatMap(_ => retryUntilGone(k8s.get[Deployment](name), retries = 40, delay = 3.seconds))
        .handleErrorWith(_ => IO.unit)

    val test = for
      created <- k8s.apply[Deployment, DeploymentApplyConfig](initialConfig, ApplyOptions(fieldManager = fieldManager))
        .map(_.getOrElse(fail("Initial apply failed")))
      _ = assertEquals(created.name, name)
      _ = assertEquals(created.spec.flatMap(_.replicas), Some(1))

      _ <- retryUntil(
        k8s.get[Deployment](name).map(_.exists(_.status.exists(_.availableReplicas >= 1))),
        retries = 40, delay = 5.seconds, label = "availableReplicas >= 1"
      )

      updated <- k8s.apply[Deployment, DeploymentApplyConfig](updatedConfig, ApplyOptions(fieldManager = fieldManager))
        .map(_.getOrElse(fail("Update apply failed")))
      _ = assertEquals(updated.spec.flatMap(_.replicas), Some(2))
      _ = assertEquals(
        updated.spec.flatMap(_.template.spec).flatMap(_.containers.headOption).map(_.image),
        Some("nginx:1.27")
      )

      _ <- retryUntil(
        k8s.get[Deployment](name).map(_.exists(_.status.exists(_.availableReplicas >= 2))),
        retries = 40, delay = 5.seconds, label = "availableReplicas >= 2"
      )
    yield ()

    test.guarantee(cleanup)
  }
