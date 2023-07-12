package skuber.autoscaling.v2

import play.api.libs.json._
import skuber.ResourceSpecification.{Names, Scope}
import skuber.{LabelSelector, LimitRange, NonCoreResourceSpecification, ObjectMeta, ObjectResource, Resource, ResourceDefinition, Timestamp}

case class HorizontalPodAutoscaler(override val kind: String = "HorizontalPodAutoscaler",
                                   override val apiVersion: String = autoscalingAPIVersion,
                                   metadata: ObjectMeta,
                                   spec: Option[HorizontalPodAutoscaler.Spec] = None,
                                   status: Option[HorizontalPodAutoscaler.Status] = None) extends ObjectResource {

  def withNamespace(namespace: String): HorizontalPodAutoscaler = {
    this.copy(metadata = this.metadata.copy(namespace = namespace))
  }

  def withSpec(spec: HorizontalPodAutoscaler.Spec): HorizontalPodAutoscaler = {
    this.copy(spec = Some(spec))
  }

  def withStatus(status: HorizontalPodAutoscaler.Status): HorizontalPodAutoscaler = {
    this.copy(status = Some(status))
  }
}

object HorizontalPodAutoscaler {
  def apply(name: String): HorizontalPodAutoscaler = {
    HorizontalPodAutoscaler(metadata = ObjectMeta(name = name))
  }

  val specification = NonCoreResourceSpecification(apiGroup = "autoscaling",
    version = "v2",
    scope = Scope.Namespaced,
    names = Names(plural = "horizontalpodautoscalers",
      singular = "horizontalpodautoscaler",
      kind = "HorizontalPodAutoscaler",
      shortNames = List("hpa")))
  implicit val stsDef: ResourceDefinition[HorizontalPodAutoscaler] = new ResourceDefinition[HorizontalPodAutoscaler] {
    def spec: NonCoreResourceSpecification = specification
  }

  implicit val stsListDef: ResourceDefinition[HorizontalPodAutoscalerList] = new ResourceDefinition[HorizontalPodAutoscalerList] {
    def spec: NonCoreResourceSpecification = specification
  }

  object MetricsSourceType extends Enumeration {
    type MetricsSourceType = Value
    val Object, Pods, Resource, External, ContainerResource = Value
  }

  sealed trait Metric {
    def `type`: MetricsSourceType.MetricsSourceType
  }

  case class ObjectMetric(`object`: ObjectMetricSource) extends Metric {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.Object
  }

  case class PodsMetric(pods: PodsMetricSource) extends Metric {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.Pods
  }

  case class ResourceMetric(resource: ResourceMetricSource) extends Metric {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.Resource
  }

  case class ExternalMetric(external: ExternalMetricSource) extends Metric {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.External
  }

  case class ContainerResourceMetric(containerResource: ContainerResourceMetricSource) extends Metric {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.ContainerResource
  }

  case class MetricIdentifier(name: String, selector: Option[LabelSelector])

  case class MetricTarget(`type`: String,
                          averageUtilization: Option[Int] = None,
                          averageValue: Option[Resource.Quantity] = None,
                          value: Option[Resource.Quantity] = None)

  case class ObjectMetricSource(describedObject: CrossVersionObjectReference,
                                metric: MetricIdentifier,
                                target: MetricTarget)

  case class PodsMetricSource(metric: MetricIdentifier, target: MetricTarget)

  case class ResourceMetricSource(name: String, target: MetricTarget)

  case class ExternalMetricSource(metric: MetricIdentifier, target: MetricTarget)

  case class ContainerResourceMetricSource(container: String, name: String, target: MetricTarget)

  sealed trait MetricStatus {
    def `type`: MetricsSourceType.MetricsSourceType
  }

  case class ObjectMetricStatusHolder(`object`: ObjectMetricStatus) extends MetricStatus {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.Object
  }

  case class PodsMetricStatusHolder(pods: PodsMetricStatus) extends MetricStatus {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.Pods
  }

  case class ResourceMetricStatusHolder(resource: ResourceMetricStatus) extends MetricStatus {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.Resource
  }

  case class ExternalMetricStatusHolder(external: ExternalMetricStatus) extends MetricStatus {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.External
  }

  case class ContainerResourceMetricStatusHolder(external: ContainerResourceMetricStatus) extends MetricStatus {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.ContainerResource
  }

  case class MetricValueStatus(averageUtilization: Option[Int],
                               averageValue: Option[Resource.Quantity],
                               value: Option[Resource.Quantity])

  case class ObjectMetricStatus(describedObject: CrossVersionObjectReference,
                                metric: MetricIdentifier,
                                current: MetricValueStatus)

  case class PodsMetricStatus(metric: MetricIdentifier, current: MetricValueStatus)

  case class ResourceMetricStatus(name: String, current: MetricValueStatus)

  case class ExternalMetricStatus(metric: MetricIdentifier, current: MetricValueStatus)

  case class ContainerResourceMetricStatus(container: String, name: String, current: MetricValueStatus)

  case class CrossVersionObjectReference(apiVersion: String,
                                         kind: String,
                                         name: String)

  case class Condition(`type`: String,
                       status: String,
                       lastTransitionTime: Option[Timestamp],
                       reason: Option[String],
                       message: Option[String])

  case class Status(observedGeneration: Option[Int],
                    lastScaleTime: Option[Timestamp],
                    currentReplicas: Option[Int],
                    desiredReplicas: Int,
                    currentMetrics: List[MetricStatus],
                    conditions: List[Condition])

  object Spec {
    def apply(apiVersion: String, kind: String, name: String): Spec = {
      new Spec(CrossVersionObjectReference(apiVersion, kind, name))
    }
  }

  case class Spec(scaleTargetRef: CrossVersionObjectReference,
                  behavior: Option[HorizontalPodAutoscalerBehavior] = None,
                  minReplicas: Option[Int] = Some(1),
                  maxReplicas: Int = 1,
                  metrics: List[Metric] = List()) {

    def addResourceMetric(metric: ResourceMetricSource): Spec = {
      this.copy(metrics = this.metrics :+ ResourceMetric(metric))
    }

    def addPodMetric(metric: PodsMetricSource): Spec = {
      this.copy(metrics = this.metrics :+ PodsMetric(metric))
    }

    def addObjectMetric(metric: ObjectMetricSource): Spec = {
      this.copy(metrics = this.metrics :+ ObjectMetric(metric))
    }

    def addExternalMetric(metric: ExternalMetricSource): Spec = {
      this.copy(metrics = this.metrics :+ ExternalMetric(metric))
    }

    def addContainerResourceMetric(metric: ContainerResourceMetricSource): Spec = {
      this.copy(metrics = this.metrics :+ ContainerResourceMetric(metric))
    }

    def withMinReplicas(replicas: Int): Spec = {
      this.copy(minReplicas = Some(replicas))
    }

    def withMaxReplicas(replicas: Int): Spec = {
      this.copy(maxReplicas = replicas)
    }

    def withBehavior(behavior: HorizontalPodAutoscalerBehavior): Spec = {
      this.copy(behavior = Some(behavior))
    }
  }

  case class HorizontalPodAutoscalerBehavior(scaleDown: Option[HPAScalingRules] = None,
                                             scaleUp: Option[HPAScalingRules] = None) {
    def withScaleDown(scaleDown: HPAScalingRules): HorizontalPodAutoscalerBehavior = {
      this.copy(scaleDown = Some(scaleDown))
    }

    def withScaleUp(scaleUp: HPAScalingRules): HorizontalPodAutoscalerBehavior = {
      this.copy(scaleUp = Some(scaleUp))
    }
  }

  case class HPAScalingRules(policies: List[HPAScalingPolicy],
                             selectPolicy: Option[String] = None,
                             stabilizationWindowSeconds: Option[Int] = None)

  case class HPAScalingPolicy(periodSeconds: Int,
                              `type`: String,
                              value: Int)

  import play.api.libs.functional.syntax._
  import play.api.libs.json.{Format, JsPath}
  import skuber.json.format._

  implicit val scalingPolicyFmt: Format[HPAScalingPolicy] = Json.format[HPAScalingPolicy]
  implicit val scalingRulesFmt: Format[HPAScalingRules] = Json.format[HPAScalingRules]
  implicit val behaviorFmt: Format[HorizontalPodAutoscalerBehavior] = Json.format[HorizontalPodAutoscalerBehavior]

  implicit val crossVersionObjectReferenceFmt: Format[CrossVersionObjectReference] = Json.format[CrossVersionObjectReference]
  implicit val conditionFmt: Format[Condition] = Json.format[Condition]
  implicit val limitRangeItemTypeFmt: Format[LimitRange.ItemType.Type] = Json.formatEnum(LimitRange.ItemType)
  implicit val metricsSourceTypeFmt: Format[MetricsSourceType.Value] = Json.formatEnum(MetricsSourceType)

  implicit val metricIdentifierFmt: Format[MetricIdentifier] = ((JsPath \ "name").format[String] and
    (JsPath \ "selector").formatNullableLabelSelector)(MetricIdentifier.apply, o => (o.name, o.selector))
  implicit val metricValueStatusFmt: Format[MetricValueStatus] = Json.format[MetricValueStatus]
  implicit val resourceMetricStatusFmt: Format[ResourceMetricStatus] = Json.format[ResourceMetricStatus]

  implicit val objectMetricStatusFmt: Format[ObjectMetricStatus] = Json.format[ObjectMetricStatus]
  implicit val podsMetricStatusFmt: Format[PodsMetricStatus] = Json.format[PodsMetricStatus]
  implicit val externalMetricStatusFmt: Format[ExternalMetricStatus] = Json.format[ExternalMetricStatus]
  implicit val containerResourceMetricStatusFmt: Format[ContainerResourceMetricStatus] = Json.format[ContainerResourceMetricStatus]

  implicit val objectMetricStatusHolderFmt: Format[ObjectMetricStatusHolder] = Json.format[ObjectMetricStatusHolder]
  implicit val podsMetricStatusHolderFmt: Format[PodsMetricStatusHolder] = Json.format[PodsMetricStatusHolder]
  implicit val resourceMetricStatusHolderFmt: Format[ResourceMetricStatusHolder] = Json.format[ResourceMetricStatusHolder]
  implicit val externalMetricStatusHolderFmt: Format[ExternalMetricStatusHolder] = Json.format[ExternalMetricStatusHolder]
  implicit val containerResourceMetricStatusHolderFmt: Format[ContainerResourceMetricStatusHolder] = Json.format[ContainerResourceMetricStatusHolder]

  implicit val metricStatusWrite: Writes[MetricStatus] = Writes[MetricStatus] {
    case s: PodsMetricStatusHolder => JsPath.write[PodsMetricStatusHolder](podsMetricStatusHolderFmt).writes(s) + ("type" -> JsString("Pods"))
    case s: ObjectMetricStatusHolder => JsPath.write[ObjectMetricStatusHolder](objectMetricStatusHolderFmt).writes(s) + ("type" -> JsString("Object"))
    case s: ResourceMetricStatusHolder => JsPath.write[ResourceMetricStatusHolder](resourceMetricStatusHolderFmt).writes(s) + ("type" -> JsString("Resource"))
    case s: ExternalMetricStatusHolder => JsPath.write[ExternalMetricStatusHolder](externalMetricStatusHolderFmt).writes(s) + ("type" -> JsString("External"))
    case s: ContainerResourceMetricStatusHolder => JsPath.write[ContainerResourceMetricStatusHolder](containerResourceMetricStatusHolderFmt).writes(s) + ("type" -> JsString("ContainerResource"))
  }

  implicit val metricStatusReads: Reads[MetricStatus] = new Reads[MetricStatus] {
    override def reads(json: JsValue): JsResult[MetricStatus] = {
      (json \ "type").as[String].toUpperCase match {
        case "OBJECT" => JsSuccess(json.as[ObjectMetricStatusHolder])
        case "PODS" => JsSuccess(json.as[PodsMetricStatusHolder])
        case "RESOURCE" => JsSuccess(json.as[ResourceMetricStatusHolder])
        case "EXTERNAL" => JsSuccess(json.as[ExternalMetricStatusHolder])
        case "CONTAINERRESOURCE" => JsSuccess(json.as[ContainerResourceMetricStatusHolder])
      }
    }
  }

  implicit val metricStatusFormat: Format[MetricStatus] = Format(metricStatusReads, metricStatusWrite)

  implicit val depStatusFmt: Format[Status] = ((JsPath \ "observedGeneration").formatNullable[Int] and
    (JsPath \ "lastScaleTime").formatNullable[Timestamp] and
    (JsPath \ "currentReplicas").formatNullable[Int] and
    (JsPath \ "desiredReplicas").format[Int] and
    (JsPath \ "currentMetrics").formatMaybeEmptyList[MetricStatus] and
    (JsPath \ "conditions").formatMaybeEmptyList[Condition]) (Status.apply,
    s => (s.observedGeneration, s.lastScaleTime, s.currentReplicas, s.desiredReplicas, s.currentMetrics, s.conditions))

  implicit val resourceMetricTargetSourceFmt: Format[MetricTarget] = Json.format[MetricTarget]
  implicit val resourceMetricSourceFmt: Format[ResourceMetricSource] = Json.format[ResourceMetricSource]

  implicit val objectMetricSourceFmt: Format[ObjectMetricSource] = Json.format[ObjectMetricSource]

  implicit val podsMetricSourceFmt: Format[PodsMetricSource] = Json.format[PodsMetricSource]

  implicit val externalMetricSourceFmt: Format[ExternalMetricSource] = Json.format[ExternalMetricSource]

  implicit val containerResourceMetricSourceFmt: Format[ContainerResourceMetricSource] = Json.format[ContainerResourceMetricSource]


  implicit val objectMetricFmt: Format[ObjectMetric] = Json.format[ObjectMetric]
  implicit val podsMetricFmt: Format[PodsMetric] = Json.format[PodsMetric]
  implicit val resourceMetricFmt: Format[ResourceMetric] = Json.format[ResourceMetric]
  implicit val externalMetricFmt: Format[ExternalMetric] = Json.format[ExternalMetric]
  implicit val containerResourceFmt: Format[ContainerResourceMetric] = Json.format[ContainerResourceMetric]

  implicit val metricReads: Reads[Metric] = new Reads[Metric] {
    override def reads(json: JsValue): JsResult[Metric] = {
      (json \ "type").as[String].toUpperCase match {
        case "OBJECT" => JsSuccess(json.as[ObjectMetric])
        case "PODS" => JsSuccess(json.as[PodsMetric])
        case "RESOURCE" => JsSuccess(json.as[ResourceMetric])
        case "EXTERNAL" => JsSuccess(json.as[ExternalMetric])
        case "CONTAINERRESOURCE" => JsSuccess(json.as[ContainerResourceMetric])
      }
    }
  }

  implicit val metricWrite: Writes[Metric] = Writes[Metric] {
    case s: PodsMetric => JsPath.write[PodsMetric](podsMetricFmt).writes(s) + ("type" -> JsString("Pods"))
    case s: ObjectMetric => JsPath.write[ObjectMetric](objectMetricFmt).writes(s) + ("type" -> JsString("Object"))
    case s: ResourceMetric => JsPath.write[ResourceMetric](resourceMetricFmt).writes(s) + ("type" -> JsString("Resource"))
    case s: ExternalMetric => JsPath.write[ExternalMetric](externalMetricFmt).writes(s) + ("type" -> JsString("External"))
    case s: ContainerResourceMetric => JsPath.write[ContainerResourceMetric](containerResourceFmt).writes(s) + ("type" -> JsString("ContainerResource"))
  }

  implicit val metricFormat: Format[Metric] = Format(metricReads, metricWrite)

  implicit val depSpecFmt: Format[Spec] = ((JsPath \ "scaleTargetRef").format[CrossVersionObjectReference] and
    (JsPath \ "behavior").formatNullable[HorizontalPodAutoscalerBehavior] and
    (JsPath \ "minReplicas").formatNullable[Int] and
    (JsPath \ "maxReplicas").format[Int] and
    (JsPath \ "metrics").formatMaybeEmptyList[Metric]) (Spec.apply, s => (s.scaleTargetRef, s.behavior, s.minReplicas, s.maxReplicas, s.metrics))

  implicit lazy val horizontalPodAutoscalerFormat: Format[HorizontalPodAutoscaler] = (objFormat and
    (JsPath \ "spec").formatNullable[Spec] and
    (JsPath \ "status").formatNullable[Status]) (HorizontalPodAutoscaler.apply, h => (h.kind, h.apiVersion, h.metadata, h.spec, h.status))

  implicit val horizontalPodAutoscalerListFormat: Format[HorizontalPodAutoscalerList] = ListResourceFormat[HorizontalPodAutoscaler]
}