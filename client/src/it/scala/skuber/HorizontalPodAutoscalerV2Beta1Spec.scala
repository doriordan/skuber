package skuber

import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.Eventually
import skuber.model.autoscaling.v2beta1.HorizontalPodAutoscaler.ResourceMetricSource
import skuber.model.apps.v1.Deployment
import skuber.model.autoscaling.v2beta1.HorizontalPodAutoscaler
import skuber.model.{Pod, Resource}

class HorizontalPodAutoscalerV2Beta1Spec extends K8SFixture with Eventually with Matchers {
  behavior of "HorizontalPodAutoscalerV2Beta1"

  it should "create a HorizontalPodAutoscaler" in { k8s =>
    val name: String = java.util.UUID.randomUUID().toString
    println(name)
    k8s.create(getNginxDeployment(name, "1.7.9")) flatMap { d =>
      k8s.create(
        HorizontalPodAutoscaler(name).withSpec(
          HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
            .withMinReplicas(1)
            .withMaxReplicas(2)
            .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None))
        )
      ).map { result =>
        assert(result.name == name)
        assert(result.spec.contains(
          HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
            .withMinReplicas(1)
            .withMaxReplicas(2)
            .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None)))
        )
      }
    }
  }

  it should "update a HorizontalPodAutoscaler" in { k8s =>
    val name: String = java.util.UUID.randomUUID().toString
    k8s.create(getNginxDeployment(name, "1.7.9")) flatMap { d =>
      k8s.create(
        HorizontalPodAutoscaler(name).withSpec(
          HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
            .withMinReplicas(1)
            .withMaxReplicas(2)
            .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None))
        )
      ).flatMap(created =>
        eventually(
          k8s.get[HorizontalPodAutoscaler](created.name).flatMap { existing =>
            val udpated = existing.withSpec(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
              .withMinReplicas(1)
              .withMaxReplicas(3)
              .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None)))

            k8s.update(udpated).map { result =>
              assert(result.name == name)
              assert(result.spec.contains(
                HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
                  .withMinReplicas(1)
                  .withMaxReplicas(3)
                  .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None))
              ))
            }
          }
        )
      )
    }
  }

  it should "delete a HorizontalPodAutoscaler" in { k8s =>
    val name: String = java.util.UUID.randomUUID().toString
    k8s.create(getNginxDeployment(name, "1.7.9")) flatMap { d =>
      k8s.create(
        HorizontalPodAutoscaler(name).withSpec(
          HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
            .withMinReplicas(1)
            .withMaxReplicas(2)
            .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None))
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
    import LabelSelector.dsl._
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(nginxTemplate).withLabelSelector("app" is "nginx")
  }

  def getNginxContainer(version: String): Container = {
    Container(name = "nginx", image = "nginx:" + version).exposePort(80)
  }
}
