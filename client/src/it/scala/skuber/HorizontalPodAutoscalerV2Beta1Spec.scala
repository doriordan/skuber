package skuber

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers}
import skuber.apps.v1.Deployment
import skuber.autoscaling.v2beta1.HorizontalPodAutoscaler
import skuber.autoscaling.v2beta1.HorizontalPodAutoscaler.ResourceMetricSource
import scala.concurrent.Future
import scala.concurrent.duration._

class HorizontalPodAutoscalerV2Beta1Spec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {

  val horizontalPodAutoscaler1: String = java.util.UUID.randomUUID().toString
  val horizontalPodAutoscaler2: String = java.util.UUID.randomUUID().toString
  val horizontalPodAutoscaler3: String = java.util.UUID.randomUUID().toString
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(horizontalPodAutoscaler1, horizontalPodAutoscaler2).map { name =>
      k8s.delete[HorizontalPodAutoscaler](name).recover { case _ => () }
    })

    results.futureValue

    results.onComplete { r =>
      k8s.close
    }

  }


  behavior of "HorizontalPodAutoscalerV2Beta1"

  it should "create a HorizontalPodAutoscaler" in { k8s =>

    println(horizontalPodAutoscaler1)
    k8s.create(getNginxDeployment(horizontalPodAutoscaler1, "1.7.9")) flatMap { d =>
      k8s.create(
        HorizontalPodAutoscaler(horizontalPodAutoscaler1).withSpec(
          HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
            .withMinReplicas(1)
            .withMaxReplicas(2)
            .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None))
        )
      ).map { result =>
        assert(result.name == horizontalPodAutoscaler1)
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

    k8s.create(getNginxDeployment(horizontalPodAutoscaler3, "1.7.9")) flatMap { d =>
      k8s.create(
        HorizontalPodAutoscaler(horizontalPodAutoscaler3).withSpec(
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
