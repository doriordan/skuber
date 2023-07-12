package skuber.autoscaling.v2

import org.specs2.mutable.Specification
import play.api.libs.json.{JsSuccess, Json}
import skuber.LabelSelector.dsl._
import skuber.{Resource, Timestamp}

import java.time.{ZoneId, ZonedDateTime}
import scala.language.reflectiveCalls

class HorizontalPodAutoscalerSpec extends Specification {

  import HorizontalPodAutoscaler._

  val lastScaleTime: Timestamp = ZonedDateTime.of(2018, 1, 1, 12, 30, 0, 0, ZoneId.of("Z"))
  val conditionTime: Timestamp = ZonedDateTime.of(2017, 1, 1, 12, 30, 0, 0, ZoneId.of("Z"))

  "A HorizontalPodAutoscaler can" >> {
    "decoded from json" >> {
      Json.parse(createJson("/exampleHorizontalPodAutoscalerV2.json")).validate[HorizontalPodAutoscaler] mustEqual JsSuccess(hpa)
    }

    "encode to json" >> {
      Json.toJson(hpa) mustEqual Json.parse(createJson("/exampleHorizontalPodAutoscalerV2.json"))
    }
  }

  val hpa: HorizontalPodAutoscaler = HorizontalPodAutoscaler("someName").withNamespace("someNamespace").withSpec(HorizontalPodAutoscaler.Spec("v2", "Deployment", "someDeploymentName")
      .withMinReplicas(2)
      .withMaxReplicas(4)
      .addObjectMetric(ObjectMetricSource(CrossVersionObjectReference("v2", "Deployment", "someDeploymentName"),
        MetricIdentifier("someObjectMetricName",Some("application" is "someObjectapp")),
        MetricTarget("Utilization", Some(10), Some(Resource.Quantity("1")), Some(Resource.Quantity("2")))))
      .addPodMetric(PodsMetricSource(MetricIdentifier("somePodsMetricName", Some("application" is "somePodsApp")),
        MetricTarget("Utilization", Some(5), Some(Resource.Quantity("4")), Some(Resource.Quantity("5")))))
      .addResourceMetric(ResourceMetricSource("someResourceName",
        MetricTarget("Utilization", Some(2), Some(Resource.Quantity("4")), Some(Resource.Quantity("3")),
        )))
      .addExternalMetric(ExternalMetricSource(MetricIdentifier("someExternalMetricsName",Some("metrics" is "someMetric")),
        MetricTarget("Utilization", Some(3), Some(Resource.Quantity("1")), Some(Resource.Quantity("7")))))
      .addContainerResourceMetric(ContainerResourceMetricSource("containerName", "metricName",
        MetricTarget("Utilization", Some(9), Some(Resource.Quantity("5")), Some(Resource.Quantity("2")))))
      .withBehavior(HorizontalPodAutoscalerBehavior(
        scaleDown = Some(HPAScalingRules(List(HPAScalingPolicy(60, "Pods", 2)))),
        scaleUp = Some(HPAScalingRules(List(HPAScalingPolicy(120, "Pods", 1))))
      )))
      .withStatus(HorizontalPodAutoscaler.Status(Some(100),
      Some(lastScaleTime),
      Some(201),
      202,
      List(ObjectMetricStatusHolder(ObjectMetricStatus(CrossVersionObjectReference("v2", "Deployment", "someDeploymentName"),
        MetricIdentifier("someObjectMetricName",Some("application" is "someObjectapp")),
        MetricValueStatus(Some(4), Some(Resource.Quantity("1")), Some(Resource.Quantity("2"))))),
        PodsMetricStatusHolder(PodsMetricStatus(MetricIdentifier("somePodsMetricName", Some("application" is "somePodsApp")),
          MetricValueStatus(Some(1), Some(Resource.Quantity("3")), Some(Resource.Quantity("4"))))),
        ResourceMetricStatusHolder(ResourceMetricStatus("someResourceName",
          MetricValueStatus(Some(10), Some(Resource.Quantity("4")), Some(Resource.Quantity("4"))))),
        ExternalMetricStatusHolder(ExternalMetricStatus(MetricIdentifier("someExternalMetricsName", Some("metrics" is "someMetric")),
          MetricValueStatus(Some(3), Some(Resource.Quantity("8")), Some(Resource.Quantity("1"))))),
        ContainerResourceMetricStatusHolder(ContainerResourceMetricStatus("containerName", "containerStatusMetricName",
          MetricValueStatus(Some(7), Some(Resource.Quantity("3")), Some(Resource.Quantity("2")))))),
      List(Condition("someType",
          "someStatus",
          Some(conditionTime),
          Some("someReason"),
          Some("someMessage")))))

  private def createJson(file: String): String = {
    val source = scala.io.Source.fromURL(getClass.getResource(file))
    try source.mkString finally source.close()
  }
}
