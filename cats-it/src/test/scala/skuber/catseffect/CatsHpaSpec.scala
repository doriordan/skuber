package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import skuber.api.client.{LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.apps.v1.Deployment
import skuber.model.autoscaling.v2.HorizontalPodAutoscaler
import skuber.model.autoscaling.v2.HorizontalPodAutoscaler.{ResourceMetricSource, UtilizationTarget}
import skuber.model.Resource
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*

class CatsHpaSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 5.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  client.test("create a HPA") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    val spec = HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", name)
      .withMinReplicas(1).withMaxReplicas(2)
      .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
      .withPodTypeScaleUpPolicy(2, 20, selectPolicy = Some("Min"), stabilizationWindowSeconds = Some(400))
      .withPercentTypeScaleDownPolicy(10, 30, selectPolicy = Some("Max"), stabilizationWindowSeconds = Some(0))
    for
      _ <- k8s.create(getNginxDeployment(name, "1.27.2")).map(_.getOrElse(fail("Create deployment failed")))
      hpa <- k8s.create(HorizontalPodAutoscaler(name = name).withSpec(spec))
        .map(_.getOrElse(fail("Create HPA failed")))
      _ = assertEquals(hpa.name, name)
      _ = assert(hpa.spec.contains(spec))
      _ <- k8s.delete[HorizontalPodAutoscaler](name)
      _ <- k8s.delete[Deployment](name)
    yield ()
  }

  client.test("update a HPA") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    val initialSpec = HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", name)
      .withMinReplicas(1).withMaxReplicas(2)
      .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
    for
      _ <- k8s.create(getNginxDeployment(name, "1.27.2")).map(_.getOrElse(fail("Create deployment failed")))
      created <- k8s.create(HorizontalPodAutoscaler(name = name).withSpec(initialSpec))
        .map(_.getOrElse(fail("Create HPA failed")))
      existing <- k8s.get[HorizontalPodAutoscaler](created.name)
        .map(_.getOrElse(fail("Get HPA failed")))
      updatedSpec = HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", name)
        .withMinReplicas(1).withMaxReplicas(3)
        .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
      result <- k8s.update(existing.withSpec(updatedSpec))
        .map(_.getOrElse(fail("Update HPA failed")))
      _ = assertEquals(result.name, name)
      _ = assert(result.spec.contains(updatedSpec))
      _ <- k8s.delete[HorizontalPodAutoscaler](name)
      _ <- k8s.delete[Deployment](name)
    yield ()
  }

  client.test("delete a HPA") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    val spec = HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", "nginx")
      .withMinReplicas(1).withMaxReplicas(2)
      .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
    for
      _ <- k8s.create(getNginxDeployment(name, "1.27.2")).map(_.getOrElse(fail("Create deployment failed")))
      created <- k8s.create(HorizontalPodAutoscaler(name = name).withSpec(spec))
        .map(_.getOrElse(fail("Create HPA failed")))
      _ <- k8s.delete[HorizontalPodAutoscaler](created.name)
      _ <- retryUntilGone(k8s.get[HorizontalPodAutoscaler](created.name), retries = 40, delay = 3.seconds)
      _ <- k8s.delete[Deployment](name)
    yield ()
  }
