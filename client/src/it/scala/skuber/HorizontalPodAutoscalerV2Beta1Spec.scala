package skuber

import java.util.UUID.randomUUID
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import skuber.apps.v1.Deployment
import skuber.autoscaling.v2beta1.HorizontalPodAutoscaler
import skuber.autoscaling.v2beta1.HorizontalPodAutoscaler.ResourceMetricSource
import scala.concurrent.Future
import scala.concurrent.duration._
import skuber.FutureUtil.FutureOps

class HorizontalPodAutoscalerV2Beta1Spec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {

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


  behavior of "HorizontalPodAutoscalerV2Beta1"

  it should "create a HorizontalPodAutoscaler" in { k8s =>

    println(horizontalPodAutoscaler1)
    k8s.create(getNginxDeployment(deployment1, "1.7.9")).valueT
    val result = k8s.create(HorizontalPodAutoscaler(horizontalPodAutoscaler1).withSpec(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
          .withMinReplicas(1)
          .withMaxReplicas(2)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None)))).valueT

    assert(result.name == horizontalPodAutoscaler1)
    assert(result.spec.contains(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
        .withMinReplicas(1)
        .withMaxReplicas(2)
        .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None))))
  }

  it should "update a HorizontalPodAutoscaler" in { k8s =>

    k8s.create(getNginxDeployment(deployment2, "1.7.9")).valueT
    val created = k8s.create(HorizontalPodAutoscaler(horizontalPodAutoscaler2).withSpec(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
          .withMinReplicas(1)
          .withMaxReplicas(2)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None)))).valueT

    Thread.sleep(5000)

    val existing = k8s.get[HorizontalPodAutoscaler](created.name).valueT
    val updated = existing.withSpec(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
      .withMinReplicas(1)
      .withMaxReplicas(3)
      .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None)))
      k8s.update(updated).valueT

    Thread.sleep(5000)
    eventually(timeout(30.seconds), interval(3.seconds)) {
      val result = k8s.get[HorizontalPodAutoscaler](created.name).valueT

      assert(result.name == horizontalPodAutoscaler2)
      assert(result.spec.contains(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
          .withMinReplicas(1)
          .withMaxReplicas(3)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None))))
    }

  }

  it should "delete a HorizontalPodAutoscaler" in { k8s =>

    k8s.create(getNginxDeployment(deployment3, "1.7.9")).valueT
    val created = k8s.create(HorizontalPodAutoscaler(horizontalPodAutoscaler3).withSpec(HorizontalPodAutoscaler.Spec("v1", "Deployment", "nginx")
          .withMinReplicas(1)
          .withMaxReplicas(2)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, Some(80), None)))).valueT

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
