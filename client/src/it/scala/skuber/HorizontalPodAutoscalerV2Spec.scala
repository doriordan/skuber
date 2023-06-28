package skuber

import org.scalatest.{BeforeAndAfterAll, Tag}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import skuber.FutureUtil.FutureOps
import skuber.apps.v1.Deployment
import skuber.autoscaling.v2.HorizontalPodAutoscaler
import skuber.autoscaling.v2.HorizontalPodAutoscaler._

import java.util.UUID.randomUUID
import scala.concurrent.Future
import scala.concurrent.duration._

class HorizontalPodAutoscalerV2Spec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {
  // Tagging the tests in order to exclude them in earlier CI k8s versions (before 1.23)
  object HorizontalPodAutoscalerV2Tag extends Tag("HorizontalPodAutoscalerV2Tag")

  val horizontalPodAutoscaler1: String = randomUUID().toString
  val horizontalPodAutoscaler2: String = randomUUID().toString
  val horizontalPodAutoscaler3: String = randomUUID().toString

  val deployment1: String = randomUUID().toString
  val deployment2: String = randomUUID().toString
  val deployment3: String = randomUUID().toString

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results1 = Future.sequence(List(horizontalPodAutoscaler1, horizontalPodAutoscaler2, horizontalPodAutoscaler3).map { name =>
      k8s.delete[HorizontalPodAutoscaler](name).withTimeout().recover { case _ => () }
    }).withTimeout()

    val results2 = {
      val futures = Future.sequence(List(deployment1, deployment2, deployment3).map(name => k8s.delete[Deployment](name).withTimeout())).withTimeout()
      futures.recover { case _ =>
        ()
      }
    }

    results1.futureValue
    results2.futureValue

    for {
      _ <- results1
      _ <- results2
    } yield {
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }

  }


  behavior of "HorizontalPodAutoscalerV2"

  it should "create a HorizontalPodAutoscaler" taggedAs HorizontalPodAutoscalerV2Tag in { k8s =>

    println(horizontalPodAutoscaler1)
    k8s.create(getNginxDeployment(deployment1, "1.7.9")).valueT
    val result = k8s.create(HorizontalPodAutoscaler(horizontalPodAutoscaler1).withSpec(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
          .withMinReplicas(1)
          .withMaxReplicas(2)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, MetricTarget("Utilization", Some(80))))
          .withBehavior(HorizontalPodAutoscalerBehavior(
            scaleDown = Some(HPAScalingRules(List(HPAScalingPolicy(60, "Pods", 2)), Some("Max"), Some(100))),
            scaleUp = Some(HPAScalingRules(List(HPAScalingPolicy(120, "Pods", 1)), Some("Max"), Some(5)))
          )))).valueT

    assert(result.name == horizontalPodAutoscaler1)
    assert(result.spec.contains(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
        .withMinReplicas(1)
        .withMaxReplicas(2)
        .addResourceMetric(ResourceMetricSource(Resource.cpu, MetricTarget("Utilization", Some(80))))
        .withBehavior(HorizontalPodAutoscalerBehavior(
          scaleDown = Some(HPAScalingRules(List(HPAScalingPolicy(60, "Pods", 2)), Some("Max"), Some(100))),
          scaleUp = Some(HPAScalingRules(List(HPAScalingPolicy(120, "Pods", 1)), Some("Max"), Some(5)))
        ))))
  }

  it should "update a HorizontalPodAutoscaler" taggedAs HorizontalPodAutoscalerV2Tag in { k8s =>

    k8s.create(getNginxDeployment(deployment2, "1.7.9")).valueT
    val created = k8s.create(HorizontalPodAutoscaler(horizontalPodAutoscaler2).withSpec(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
          .withMinReplicas(1)
          .withMaxReplicas(2)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, MetricTarget("Utilization", Some(80)))))).valueT

    Thread.sleep(5000)

    val existing = k8s.get[HorizontalPodAutoscaler](created.name).valueT
    val updated = existing.withSpec(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
      .withMinReplicas(1)
      .withMaxReplicas(3)
      .addResourceMetric(ResourceMetricSource(Resource.cpu, MetricTarget("Utilization", Some(80)))))
      k8s.update(updated).valueT

    Thread.sleep(5000)
    eventually(timeout(30.seconds), interval(3.seconds)) {
      val result = k8s.get[HorizontalPodAutoscaler](created.name).valueT

      assert(result.name == horizontalPodAutoscaler2)
      assert(result.spec.contains(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
          .withMinReplicas(1)
          .withMaxReplicas(3)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, MetricTarget("Utilization", Some(80))))))
    }

  }

  it should "delete a HorizontalPodAutoscaler" taggedAs HorizontalPodAutoscalerV2Tag in { k8s =>

    k8s.create(getNginxDeployment(deployment3, "1.7.9")).valueT
    val created = k8s.create(HorizontalPodAutoscaler(horizontalPodAutoscaler3).withSpec(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
          .withMinReplicas(1)
          .withMaxReplicas(2)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, MetricTarget("Utilization", Some(80)))))).valueT

    Thread.sleep(5000)

    k8s.delete[HorizontalPodAutoscaler](created.name).valueT

    eventually(timeout(30.seconds), interval(3.seconds)) {
      whenReady(k8s.get[HorizontalPodAutoscaler](created.name).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }


  }


}
