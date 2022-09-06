package skuber.autoscaling.v2beta1

import java.time.{ZoneId, ZonedDateTime}
import org.specs2.mutable.Specification
import play.api.libs.json.{JsSuccess, Json}
import skuber.{Resource, Timestamp}
import skuber.LabelSelector.dsl._
import scala.language.reflectiveCalls

class HorizontalPodAutoscalerSpec extends Specification {

  import HorizontalPodAutoscaler._

  val lastScaleTime: Timestamp = ZonedDateTime.of(2018, 1, 1, 12, 30, 0, 0, ZoneId.of("Z"))
  val conditionTime: Timestamp = ZonedDateTime.of(2017, 1, 1, 12, 30, 0, 0, ZoneId.of("Z"))

  "A HorizontalPodAutoscaler can" >> {
    "decoded from json" >> {
      Json.parse(createJson("/exampleHorizontalPodAutoscaler.json")).validate[HorizontalPodAutoscaler] mustEqual JsSuccess(hpa)
    }

    "encode to json" >> {
      Json.toJson(hpa) mustEqual Json.parse(createJson("/exampleHorizontalPodAutoscaler.json"))
    }
  }

  val hpa: HorizontalPodAutoscaler = HorizontalPodAutoscaler("someName").withNamespace("someNamespace").withSpec(HorizontalPodAutoscaler.Spec("v2", "Deployment", "someDeploymentName")
      .withMinReplicas(2)
      .withMaxReplicas(4).addObjectMetric(ObjectMetricSource(CrossVersionObjectReference("v2", "Deployment", "someDeploymentName"),
        "someObjectMetricName",
        Resource.Quantity("1"),
        Some("application" is "someObjectapp"),
        Some(Resource.Quantity("2")))).addPodMetric(PodsMetricSource("somePodsMetricName",
        Resource.Quantity("3"),
        Some("application" is "somePodsApp"))).addResourceMetric(ResourceMetricSource("someResourceName",
        Some(10),
        Some(Resource.Quantity("4")))).addExternalMetric(ExternalMetricSource("someExternalMetricsName",
        Some("metrics" is "someMetric"),
        Some(Resource.Quantity("5")),
        Some(Resource.Quantity("6"))))).withStatus(HorizontalPodAutoscaler.Status(Some(100),
      Some(lastScaleTime),
      201,
      202,
      List(ObjectMetricStatusHolder(ObjectMetricStatus(CrossVersionObjectReference("v2", "Deployment", "someDeploymentName"),
            "someObjectMetricName",
            Resource.Quantity("1"),
            Some("application" is "someObjectapp"),
            Some(Resource.Quantity("2")))),
        PodsMetricStatusHolder(PodsMetricStatus("somePodsMetricName",
            Resource.Quantity("3"),
            Some("application" is "somePodsApp"))),
        ResourceMetricStatusHolder(ResourceMetricStatus("someResourceName",
            Some(10),
            Some(Resource.Quantity("4")))),
        ExternalMetricStatusHolder(ExternalMetricStatus("someExternalMetricsName",
            Some("metrics" is "someMetric"),
            Some(Resource.Quantity("5")),
            Some(Resource.Quantity("6"))))),
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
