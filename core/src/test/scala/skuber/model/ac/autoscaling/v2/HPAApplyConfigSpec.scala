package skuber.model.ac.autoscaling.v2

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.autoscaling.v2.HorizontalPodAutoscaler
import skuber.json.format._

class HPAApplyConfigSpec extends AnyFlatSpec with Matchers {

  "HPAApplyConfig" should "be constructed by name" in {
    val hpa = HPAApplyConfig("my-hpa")
    hpa.name shouldBe "my-hpa"
    hpa.kind shouldBe "HorizontalPodAutoscaler"
    hpa.apiVersion shouldBe "autoscaling/v2"
  }

  it should "serialize with spec fields" in {
    val hpa = HPAApplyConfig("my-hpa")
      .withSpec(HPASpecApplyConfig()
        .withScaleTargetRef(HorizontalPodAutoscaler.CrossVersionObjectReference("apps/v1", "Deployment", "my-deployment"))
        .withMinReplicas(2)
        .withMaxReplicas(10)
      )
    val json = Json.toJson(hpa)
    (json \ "kind").as[String] shouldBe "HorizontalPodAutoscaler"
    (json \ "apiVersion").as[String] shouldBe "autoscaling/v2"
    (json \ "spec" \ "minReplicas").as[Int] shouldBe 2
    (json \ "spec" \ "maxReplicas").as[Int] shouldBe 10
    (json \ "spec" \ "scaleTargetRef" \ "name").as[String] shouldBe "my-deployment"
  }

  it should "extend ApplyConfiguration[HorizontalPodAutoscaler]" in {
    val hpa: ApplyConfiguration[HorizontalPodAutoscaler] = HPAApplyConfig("my-hpa")
    hpa.name shouldBe "my-hpa"
  }

  it should "support adding metrics" in {
    val spec = HPASpecApplyConfig()
      .withScaleTargetRef(HorizontalPodAutoscaler.CrossVersionObjectReference("apps/v1", "Deployment", "my-dep"))
      .addMetric(HorizontalPodAutoscaler.ResourceMetric(
        HorizontalPodAutoscaler.ResourceMetricSource("cpu", HorizontalPodAutoscaler.UtilizationTarget(80))
      ))
    spec.metrics.get.size shouldBe 1
  }
}
