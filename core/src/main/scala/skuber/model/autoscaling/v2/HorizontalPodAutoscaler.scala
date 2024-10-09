package skuber.model.autoscaling.v2

import play.api.libs.json._
import Reads._
import Writes._

import skuber.model.ResourceSpecification.{Names, Scope}
import skuber.model.autoscaling.v2.HorizontalPodAutoscaler.Spec.{Behavior, HPAScalingPolicy, HPAScalingRules}
import skuber.model.{LabelSelector, LimitRange, NonCoreResourceSpecification, ObjectMeta, ObjectResource, Resource, ResourceDefinition, Timestamp}

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

  val specification = NonCoreResourceSpecification(
    apiGroup = "autoscaling",
    version = "v2",
    scope = Scope.Namespaced,
    names = Names(
      plural = "horizontalpodautoscalers",
      singular = "horizontalpodautoscaler",
      kind = "HorizontalPodAutoscaler",
      shortNames = List("hpa")
    )
  )
  implicit val hpaDef: ResourceDefinition[HorizontalPodAutoscaler] = new ResourceDefinition[HorizontalPodAutoscaler] {
    def spec: NonCoreResourceSpecification = specification
  }

  implicit val hpaListDef: ResourceDefinition[HorizontalPodAutoscalerList] = new ResourceDefinition[HorizontalPodAutoscalerList] {
    def spec: NonCoreResourceSpecification = specification
  }

  sealed trait MetricTarget {
    def `type`: String
  }

  object MetricTarget {
    val Utilization = "Utilization"
    val Value = "Value"
    val AverageValue = "AverageValue"
  }
  case class UtilizationTarget(
    averageUtilization: Int,
    override val `type`: String = MetricTarget.Utilization

  ) extends MetricTarget

  case class ValueTarget(
    value: Resource.Quantity,
    override val `type`: String = MetricTarget.Value
  ) extends MetricTarget

  case class AverageValueTarget(
    averageValue: Resource.Quantity,
    override val `type`: String = MetricTarget.AverageValue,
  ) extends MetricTarget

  object MetricsSourceType extends Enumeration {
    type MetricsSourceType = Value
    val Object, Pods, Resource, External = Value
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

  case class ResourceMetric(resource: ResourceMetricSource) extends Metric{
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.Resource
  }

  case class ExternalMetric(external: ExternalMetricSource) extends Metric{
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.External
  }

  case class ObjectMetricSource(target: CrossVersionObjectReference,
                                metricName: String,
                                targetValue: Resource.Quantity,
                                selector: Option[LabelSelector],
                                averageValue: Option[Resource.Quantity])

  case class PodsMetricSource(metricName: String,
                              targetAverageValue: Resource.Quantity,
                              selector: Option[LabelSelector])

  case class ResourceMetricSource(name: String,
                                  target: MetricTarget)

  case class ExternalMetricSource(metricName: String,
                                  metricSelector: Option[LabelSelector],
                                  targetValue: Option[Resource.Quantity],
                                  targetAverageValue: Option[Resource.Quantity])

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

  case class ObjectMetricStatus(target: CrossVersionObjectReference,
                                metricName: String,
                                currentValue: Resource.Quantity,
                                selector: Option[LabelSelector],
                                averageValue: Option[Resource.Quantity])

  case class PodsMetricStatus(metricName: String,
                              currentAverageValue: Resource.Quantity,
                              selector: Option[LabelSelector])

  case class ResourceMetricStatus(name: String,
                                  currentAverageUtilization: Option[Int],
                                  currentAverageValue: Option[Resource.Quantity])

  case class ExternalMetricStatus(metricName: String,
                                  metricSelector: Option[LabelSelector],
                                  currentValue: Option[Resource.Quantity],
                                  currentAverageValue: Option[Resource.Quantity])

  case class CrossVersionObjectReference(apiVersion: String,
                                         kind: String,
                                         name: String)

  case class Condition(`type`: String,
                       status: String,
                       lastTransitionTime: Option[Timestamp],
                       reason: Option[String],
                       message: Option[String])

  case class Status(observedGeneration:Option[Int],
                    lastScaleTime:Option[Timestamp],
                    currentReplicas: Option[Int],
                    desiredReplicas: Int,
                    currentMetrics: List[MetricStatus],
                    conditions: List[Condition])

  object Spec {
    def apply(apiVersion: String, kind: String, name: String): Spec = {
      new Spec(CrossVersionObjectReference(apiVersion, kind, name))
    }

    case class HPAScalingPolicy(
      `type`: String,
      value: Int,
      periodSeconds: Int
    )

    object HPAScalingPolicy {
      val Pods = "Pods"
      val Percent = "Percent"
    }

    case class HPAScalingRules(
      policies: Option[List[HPAScalingPolicy]],
      selectPolicy: Option[String],
      stabilizationWindowSeconds: Option[Int]
    )
    case class Behavior(
      scaleDown: Option[HPAScalingRules],
      scaleUp: Option[HPAScalingRules]
    )

  }

  case class Spec(scaleTargetRef: CrossVersionObjectReference,
                  minReplicas: Option[Int] = Some(1),
                  maxReplicas: Int = 1,
                  metrics: List[Metric] = List(),
                  behavior: Option[Behavior] = None
  ) {

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

    def withMinReplicas(replicas: Int): Spec = {
      this.copy(minReplicas = Some(replicas))
    }

    def withMaxReplicas(replicas: Int): Spec = {
      this.copy(maxReplicas = replicas)
    }

    def withBehaviour(scaleDown: Option[HPAScalingRules] = None, scaleUp: Option[HPAScalingRules] = None): Spec = {
      this.copy(behavior = Some(Behavior(scaleDown, scaleUp)))
    }

    def withScaleDown(scaledown: Option[HPAScalingRules]): Spec = {
      this.behavior match {
        case None =>
          this.copy(behavior = Some(Behavior(scaleDown = scaledown, scaleUp = None)))
        case Some(Behavior(_, scaleup)) =>
          this.copy(behavior = Some(Behavior(scaleDown = scaledown, scaleUp = scaleup)))
      }
    }

    def withScaleUp(scaleup: Option[HPAScalingRules]): Spec = {
      this.behavior match {
        case None =>
          this.copy(behavior = Some(Behavior(scaleDown = None, scaleUp = scaleup)))
        case Some(Behavior(scaledwn, _)) =>
          this.copy(behavior = Some(Behavior(scaleDown = scaledwn, scaleUp = scaleup)))
      }
    }

    def withPodTypeScaleDownPolicy(podCount: Int, periodSeconds: Int, selectPolicy: Option[String] = None,stabilizationWindowSeconds: Option[Int] = None) : Spec = {
      // NOTE overwrites any existing scaledown rules - use withScaleDown if you need multiple scale up policies
      val scaledown = Some(HPAScalingRules(
        policies = Some(List(HPAScalingPolicy(HPAScalingPolicy.Pods, podCount, periodSeconds))),
        selectPolicy = selectPolicy,
        stabilizationWindowSeconds = stabilizationWindowSeconds))
      withScaleDown(scaledown)
    }

    def withPercentTypeScaleDownPolicy(percent: Int, periodSeconds: Int, selectPolicy: Option[String] = None, stabilizationWindowSeconds: Option[Int] = None): Spec = {
      // NOTE overwrites any existing scaledown rules - use withScaledown if you need multiple scale up policies
      val scaledown = Some(HPAScalingRules(
        policies = Some(List(HPAScalingPolicy(HPAScalingPolicy.Percent, percent, periodSeconds))),
        selectPolicy = selectPolicy,
        stabilizationWindowSeconds = stabilizationWindowSeconds))
      withScaleDown(scaledown)
    }

    def withPodTypeScaleUpPolicy(podCount: Int, periodSeconds: Int, selectPolicy: Option[String] = None, stabilizationWindowSeconds: Option[Int] = None): Spec = {
      // NOTE overwrites any existing scaleup rules  - use withScaleUp if you need multiple scale up policies
      val scaleup= Some(HPAScalingRules(
        policies = Some(List(HPAScalingPolicy(HPAScalingPolicy.Pods, podCount, periodSeconds))),
        selectPolicy = selectPolicy,
        stabilizationWindowSeconds = stabilizationWindowSeconds))
      withScaleUp(scaleup)
    }

    def withPercentTypeScaleUpPolicy(percent: Int, periodSeconds: Int, selectPolicy: Option[String] = None, stabilizationWindowSeconds: Option[Int] = None): Spec = {
      // NOTE overwrites any existing scaleup rules - use withScaleUp if you need multiple scale up policies
      val scaleup = Some(HPAScalingRules(
        policies = Some(List(HPAScalingPolicy(HPAScalingPolicy.Percent, percent, periodSeconds))),
        selectPolicy = selectPolicy,
        stabilizationWindowSeconds = stabilizationWindowSeconds))
      withScaleUp(scaleup)
    }
  }


  import play.api.libs.functional.syntax._
  import play.api.libs.json.{Format, JsPath}
  import skuber.json.format._

  implicit val crossVersionObjectReferenceFmt: Format[CrossVersionObjectReference] = Json.format[CrossVersionObjectReference]
  implicit val timestampFmt: Format[Timestamp] = Format(timeReads, timeWrites)
  implicit val conditionFmt: Format[Condition] = Json.format[Condition]
  implicit val limitRangeItemTypeFmt: Format[LimitRange.ItemType.Type] = enumFormat(LimitRange.ItemType)
  implicit val metricsSourceTypeFmt: Format[MetricsSourceType.Value] = Format(enumReads(MetricsSourceType), enumWrites(MetricsSourceType))

  implicit val resourceMetricStatusFmt: Format[ResourceMetricStatus] = Json.format[ResourceMetricStatus]

  implicit val objectMetricStatusFmt: Format[ObjectMetricStatus] = (
    (JsPath \ "target").format[CrossVersionObjectReference] and
    (JsPath \ "metricName").format[String] and
      (JsPath \ "currentValue").format[Resource.Quantity] and
      (JsPath \ "selector").formatNullableLabelSelector and
      (JsPath \ "averageValue").formatNullable[Resource.Quantity]
    ) (ObjectMetricStatus.apply, o => (o.target, o.metricName, o.currentValue, o.selector, o.averageValue))

  implicit val podsMetricStatusFmt: Format[PodsMetricStatus] = (
    (JsPath \ "metricName").format[String] and
      (JsPath \ "currentAverageValue").format[Resource.Quantity] and
      (JsPath \ "selector").formatNullableLabelSelector
    ) (PodsMetricStatus.apply, p => (p.metricName, p.currentAverageValue, p.selector))

  implicit val externalMetricStatusFmt: Format[ExternalMetricStatus] = (
    (JsPath \ "metricName").format[String] and
      (JsPath \ "metricSelector").formatNullableLabelSelector and
      (JsPath \ "currentValue").formatNullable[Resource.Quantity] and
      (JsPath \ "currentAverageValue").formatNullable[Resource.Quantity]
    ) (ExternalMetricStatus.apply, e => (e.metricName, e.metricSelector, e.currentValue, e.currentAverageValue))


  implicit val objectMetricStatusHolderFmt: Format[ObjectMetricStatusHolder] = Json.format[ObjectMetricStatusHolder]
  implicit val podsMetricStatusHolderFmt: Format[PodsMetricStatusHolder] = Json.format[PodsMetricStatusHolder]
  implicit val resourceMetricStatusHolderFmt: Format[ResourceMetricStatusHolder] = Json.format[ResourceMetricStatusHolder]
  implicit val externalMetricStatusHolderFmt: Format[ExternalMetricStatusHolder] = Json.format[ExternalMetricStatusHolder]

  implicit val metricStatusWrite: Writes[MetricStatus] = Writes[MetricStatus] {
    case s: PodsMetricStatusHolder => JsPath.write[PodsMetricStatusHolder](podsMetricStatusHolderFmt).writes(s) + ("type" -> JsString("Pods"))
    case s: ObjectMetricStatusHolder => JsPath.write[ObjectMetricStatusHolder](objectMetricStatusHolderFmt).writes(s) + ("type" -> JsString("Object"))
    case s: ResourceMetricStatusHolder => JsPath.write[ResourceMetricStatusHolder](resourceMetricStatusHolderFmt).writes(s) + ("type" -> JsString("Resource"))
    case s: ExternalMetricStatusHolder => JsPath.write[ExternalMetricStatusHolder](externalMetricStatusHolderFmt).writes(s) + ("type" -> JsString("External"))
  }

  implicit val metricStatusReads: Reads[MetricStatus] = new Reads[MetricStatus] {
    override def reads(json: JsValue): JsResult[MetricStatus] = {
      (json \ "type").as[String].toUpperCase match {
        case "OBJECT" => JsSuccess(json.as[ObjectMetricStatusHolder])
        case "PODS" => JsSuccess(json.as[PodsMetricStatusHolder])
        case "RESOURCE" => JsSuccess(json.as[ResourceMetricStatusHolder])
        case "EXTERNAL" => JsSuccess(json.as[ExternalMetricStatusHolder])
      }
    }
  }

  implicit val metricStatusFormat: Format[MetricStatus] = Format(metricStatusReads, metricStatusWrite)

  implicit val depStatusFmt: Format[Status] = (
    (JsPath \ "observedGeneration").formatNullable[Int] and
      (JsPath \ "lastScaleTime").formatNullable[Timestamp] and
      (JsPath \ "currentReplicas").formatNullable[Int] and
      (JsPath \ "desiredReplicas").format[Int] and
      (JsPath \ "currentMetrics").formatMaybeEmptyList[MetricStatus] and
      (JsPath \ "conditions").formatMaybeEmptyList[Condition]
    ) (Status.apply, d => (d.observedGeneration, d.lastScaleTime, d.currentReplicas, d.desiredReplicas, d.currentMetrics, d.conditions))

  implicit val utilizationTargetFmt: Format[UtilizationTarget] = Json.format[UtilizationTarget]
  implicit val valueTargetFmt: Format[ValueTarget] = Json.format[ValueTarget]
  implicit val averageValueTargetFmt: Format[AverageValueTarget] = Json.format[AverageValueTarget]

  implicit val metricTargetReads: Reads[MetricTarget] = new Reads[MetricTarget] {
    override def reads(json: JsValue): JsResult[MetricTarget] = {
      (json \ "type").as[String].toUpperCase match {
        case "UTILIZATION" => JsSuccess(UtilizationTarget((json \ "averageUtilization").as[Int]))
        case "VALUE" => JsSuccess(ValueTarget(Resource.Quantity((json \ "value").as[String])))
        case "AVERAGEVALUE" => JsSuccess(AverageValueTarget(Resource.Quantity((json \ "averaqeValue").as[String])))
      }
    }
  }
  implicit val metricTargetWrite: Writes[MetricTarget] = Writes[MetricTarget] {
    case u: UtilizationTarget => JsPath.write[UtilizationTarget](utilizationTargetFmt).writes(u)
    case v: ValueTarget =>  JsPath.write[ValueTarget](valueTargetFmt).writes(v)
    case a: AverageValueTarget => JsPath.write[AverageValueTarget](averageValueTargetFmt).writes(a)
  }

  implicit val metricsTargetFormat: Format[MetricTarget] = Format(metricTargetReads, metricTargetWrite)

  implicit val objectMetricSourceFmt: Format[ObjectMetricSource] = (
    (JsPath \ "target").format[CrossVersionObjectReference] and
      (JsPath \ "metricName").format[String] and
      (JsPath \ "targetValue").format[Resource.Quantity] and
      (JsPath \ "selector").formatNullableLabelSelector and
      (JsPath \ "averageValue").formatNullable[Resource.Quantity]
    ) (ObjectMetricSource.apply, o => (o.target, o.metricName, o.targetValue, o.selector, o.averageValue))

  implicit val podsMetricSourceFmt: Format[PodsMetricSource] = (
    (JsPath \ "metricName").format[String] and
      (JsPath \ "targetAverageValue").format[Resource.Quantity] and
      (JsPath \ "selector").formatNullableLabelSelector
    ) (PodsMetricSource.apply, p => (p.metricName, p.targetAverageValue, p.selector))

  implicit val externalMetricSourceFmt: Format[ExternalMetricSource] = (
    (JsPath \ "metricName").format[String] and
      (JsPath \ "metricSelector").formatNullableLabelSelector and
      (JsPath \ "targetValue").formatNullable[Resource.Quantity] and
      (JsPath \ "targetAverageValue").formatNullable[Resource.Quantity]
    ) (ExternalMetricSource.apply, o => (o.metricName, o.metricSelector, o.targetValue, o.targetAverageValue))

  implicit val resourceMetricSourceFmt: Format[ResourceMetricSource] = Json.format[ResourceMetricSource]

  implicit val objectMetricFmt: Format[ObjectMetric] = Json.format[ObjectMetric]
  implicit val podsMetricFmt: Format[PodsMetric] = Json.format[PodsMetric]
  implicit val resourceMetricFmt: Format[ResourceMetric] = Json.format[ResourceMetric]
  implicit val externalMetricFmt: Format[ExternalMetric] = Json.format[ExternalMetric]

  implicit val metricReads: Reads[Metric] = new Reads[Metric] {
    override def reads(json: JsValue): JsResult[Metric] = {
      (json \ "type").as[String].toUpperCase match {
        case "OBJECT" => JsSuccess(json.as[ObjectMetric])
        case "PODS" => JsSuccess(json.as[PodsMetric])
        case "RESOURCE" => JsSuccess(json.as[ResourceMetric])
        case "EXTERNAL" => JsSuccess(json.as[ExternalMetric])
      }
    }
  }

  implicit val metricWrite: Writes[Metric] = Writes[Metric] {
    case s: PodsMetric => JsPath.write[PodsMetric](podsMetricFmt).writes(s) + ("type" -> JsString("Pods"))
    case s: ObjectMetric => JsPath.write[ObjectMetric](objectMetricFmt).writes(s) + ("type" -> JsString("Object"))
    case s: ResourceMetric => JsPath.write[ResourceMetric](resourceMetricFmt).writes(s) + ("type" -> JsString("Resource"))
    case s: ExternalMetric => JsPath.write[ExternalMetric](externalMetricFmt).writes(s) + ("type" -> JsString("External"))
  }

  implicit val metricFormat: Format[Metric] = Format(metricReads, metricWrite)
  implicit val scalingPolicyFmt: Format[Spec.HPAScalingPolicy] = Json.format[HPAScalingPolicy]
  implicit val scalingRulesFmt: Format[Spec.HPAScalingRules] = Json.format[HPAScalingRules]
  implicit val behaviorFmt: Format[Spec.Behavior] = Json.format[Behavior]

  implicit val hpaSpecFmt: Format[Spec] = (
      (JsPath \ "scaleTargetRef").format[CrossVersionObjectReference] and
      (JsPath \ "minReplicas").formatNullable[Int] and
      (JsPath \ "maxReplicas").format[Int] and
      (JsPath \ "metrics").formatMaybeEmptyList[Metric] and
      (JsPath \ "behavior").formatNullable[Behavior]
    ) (Spec.apply, h => (h.scaleTargetRef, h.minReplicas, h.maxReplicas, h.metrics, h.behavior))

  implicit lazy val horizontalPodAutoscalerFormat: Format[HorizontalPodAutoscaler] = (
    objFormat and
      (JsPath \ "spec").formatNullable[Spec] and
      (JsPath \ "status").formatNullable[Status]
    )(HorizontalPodAutoscaler.apply, h => (h.kind, h.apiVersion, h.metadata, h.spec, h.status))

  implicit val horizontalPodAutoscalerListFormat: Format[HorizontalPodAutoscalerList] = ListResourceFormat[HorizontalPodAutoscaler]
}