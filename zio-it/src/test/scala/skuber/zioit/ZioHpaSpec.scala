package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import skuber.json.format.*
import skuber.model.Resource
import skuber.model.apps.v1.Deployment
import skuber.model.autoscaling.v2.HorizontalPodAutoscaler
import skuber.model.autoscaling.v2.HorizontalPodAutoscaler.{ResourceMetricSource, UtilizationTarget}
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

object ZioHpaSpec:

  def spec = suite("ZIO HorizontalPodAutoscaler")(

    test("create a HPA") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        val spec = HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", name)
          .withMinReplicas(1).withMaxReplicas(2)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
          .withPodTypeScaleUpPolicy(2, 20, selectPolicy = Some("Min"), stabilizationWindowSeconds = Some(400))
          .withPercentTypeScaleDownPolicy(10, 30, selectPolicy = Some("Max"), stabilizationWindowSeconds = Some(0))
        for
          _ <- k8s.create(getNginxDeployment(name, "1.27.2"))
          hpa <- k8s.create(HorizontalPodAutoscaler(name = name).withSpec(spec))
          _ = assert(hpa.name == name)
          _ = assert(hpa.spec.contains(spec))
          _ <- k8s.delete[HorizontalPodAutoscaler](name)
          _ <- k8s.delete[Deployment](name)
        yield assertTrue(true)
      }
    },

    test("update a HPA") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        val initialSpec = HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", name)
          .withMinReplicas(1).withMaxReplicas(2)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
        for
          _ <- k8s.create(getNginxDeployment(name, "1.27.2"))
          created <- k8s.create(HorizontalPodAutoscaler(name = name).withSpec(initialSpec))
          existing <- k8s.get[HorizontalPodAutoscaler](created.name)
          updatedSpec = HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", name)
            .withMinReplicas(1).withMaxReplicas(3)
            .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
          result <- k8s.update(existing.withSpec(updatedSpec))
          _ = assert(result.name == name)
          _ = assert(result.spec.contains(updatedSpec))
          _ <- k8s.delete[HorizontalPodAutoscaler](name)
          _ <- k8s.delete[Deployment](name)
        yield assertTrue(true)
      }
    },

    test("delete a HPA") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        val spec = HorizontalPodAutoscaler.Spec("apps/v1", "Deployment", "nginx")
          .withMinReplicas(1).withMaxReplicas(2)
          .addResourceMetric(ResourceMetricSource(Resource.cpu, UtilizationTarget(80)))
        for
          _ <- k8s.create(getNginxDeployment(name, "1.27.2"))
          created <- k8s.create(HorizontalPodAutoscaler(name = name).withSpec(spec))
          _ <- k8s.delete[HorizontalPodAutoscaler](created.name)
          _ <- retryUntilGone(k8s.get[HorizontalPodAutoscaler](created.name), retries = 40, delay = 3.seconds)
          _ <- k8s.delete[Deployment](name)
        yield assertTrue(true)
      }
    }

  )
