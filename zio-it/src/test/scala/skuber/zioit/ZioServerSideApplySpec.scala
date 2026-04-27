package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import skuber.api.client.{ApplyOptions, DeleteOptions, DeletePropagation}
import skuber.json.format.*
import skuber.model.LabelSelector
import skuber.model.LabelSelector.dsl.*
import skuber.model.ac.apps.v1.{DeploymentApplyConfig, DeploymentSpecApplyConfig}
import skuber.model.ac.{ContainerApplyConfig, PodSpecApplyConfig, PodTemplateSpecApplyConfig}
import skuber.model.apps.v1.Deployment
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

object ZioServerSideApplySpec:

  def spec = suite("ZIO Server-Side Apply")(

    test("server-side apply creates and updates a deployment") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
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
            .ignoreLogged

        val testLogic = for
          created <- k8s.apply[Deployment, DeploymentApplyConfig](initialConfig, ApplyOptions(fieldManager = fieldManager))
          _ = assert(created.name == name)
          _ = assert(created.spec.flatMap(_.replicas).contains(1))

          _ <- retryUntil(
            k8s.get[Deployment](name).map(d => d.status.exists(_.availableReplicas >= 1)),
            retries = 40, delay = 5.seconds, label = "availableReplicas >= 1"
          )

          updated <- k8s.apply[Deployment, DeploymentApplyConfig](updatedConfig, ApplyOptions(fieldManager = fieldManager))
          _ = assert(updated.spec.flatMap(_.replicas).contains(2))
          _ = assert(
            updated.spec.flatMap(_.template.spec).flatMap(_.containers.headOption).map(_.image).contains("nginx:1.27")
          )

          _ <- retryUntil(
            k8s.get[Deployment](name).map(d => d.status.exists(_.availableReplicas >= 2)),
            retries = 40, delay = 5.seconds, label = "availableReplicas >= 2"
          )
        yield assertTrue(true)

        testLogic.ensuring(cleanup)
      }
    }

  )
