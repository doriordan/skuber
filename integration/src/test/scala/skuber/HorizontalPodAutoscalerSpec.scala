package skuber

import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import skuber.api.client._
import skuber.model.LabelSelector.dsl._
import skuber.model.apps.v1.Deployment
import skuber.model.autoscaling.v2.HorizontalPodAutoscaler
import skuber.model.autoscaling.v2.HorizontalPodAutoscaler.{ResourceMetricSource, UtilizationTarget}
import skuber.model.{Container, LabelSelector, Pod, Resource}

import scala.language.{postfixOps, reflectiveCalls}

/**
 * Shared integration tests for HorizontalPodAutoscaler operations that work with both Akka and Pekko clients.
 * The concrete fixture (AkkaK8SFixture or PekkoK8SFixture) is mixed in via build configuration.
 */
abstract class HorizontalPodAutoscalerSpec extends K8SFixture[_, _, _] with Eventually with Matchers {
  behavior of "HorizontalPodAutoscalerV2"

  it should "create a HorizontalPodAutoscaler" in { k8s =>
    val name: String = java.util.UUID.randomUUID().toString
    val spec = HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", name)
        .withMinReplicas(1)
        .withMaxReplicas(2)
        .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
        .withPodTypeScaleUpPolicy(2, 20, selectPolicy = Some("Min"), stabilizationWindowSeconds = Some(400))
        .withPercentTypeScaleDownPolicy(10, 30, selectPolicy = Some("Max"), stabilizationWindowSeconds = Some(0))
    k8s.create(getNginxDeployment(name, "1.27.2")).flatMap { d =>
      k8s.create(HorizontalPodAutoscaler(name).withSpec(spec))
    }.map { result =>
        assert(result.name == name)
        assert(result.spec.contains(spec))
      }
    }

  it should "update a HorizontalPodAutoscaler" in { k8s =>
    val name: String = java.util.UUID.randomUUID().toString
    k8s.create(getNginxDeployment(name, "1.27.2")) flatMap { d =>
      k8s.create(
        HorizontalPodAutoscaler(name).withSpec(
          HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", name)
            .withMinReplicas(1)
            .withMaxReplicas(2)
            .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
        )
      ).flatMap(created =>
        eventually(
          k8s.get[HorizontalPodAutoscaler](created.name).flatMap { existing =>
            val updated = existing.withSpec(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
              .withMinReplicas(1)
              .withMaxReplicas(3)
              .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80))))

            k8s.update(updated).map { result =>
              assert(result.name == name)
              assert(result.spec.contains(
                HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", name)
                  .withMinReplicas(1)
                  .withMaxReplicas(3)
                  .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
              ))
            }
          }
        )
      )
    }
  }

  it should "delete a HorizontalPodAutoscaler" in { k8s =>
    val name: String = java.util.UUID.randomUUID().toString
    k8s.create(getNginxDeployment(name, "1.27.2")) flatMap { d =>
      k8s.create(
        HorizontalPodAutoscaler(name).withSpec(
          HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", "nginx")
            .withMinReplicas(1)
            .withMaxReplicas(2)
            .addResourceMetric(ResourceMetricSource(Resource.cpu,UtilizationTarget(80) ))
        )
      ).flatMap { created =>
        k8s.delete[HorizontalPodAutoscaler](created.name).flatMap { deleteResult =>
          k8s.get[HorizontalPodAutoscaler](created.name).map { x =>
            assert(false)
          } recoverWith {
            case ex: K8SException if ex.status.code.contains(404) => assert(true)
            case _ => assert(false)
          }
        }
      }
    }
  }

  def getNginxDeployment(name: String, version: String): Deployment = {
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(nginxTemplate).withLabelSelector("app" is "nginx")
  }

  def getNginxContainer(version: String): Container = {
    Container(name = "nginx", image = "nginx:" + version).exposePort(80)
  }
}