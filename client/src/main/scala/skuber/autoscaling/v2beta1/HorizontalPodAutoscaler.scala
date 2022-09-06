package skuber.autoscaling.v2beta1

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
    version = "v2beta1",
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

  case class ResourceMetric(resource: ResourceMetricSource) extends Metric {
    val `type`: MetricsSourceType.MetricsSourceType = MetricsSourceType.Resource
  }

  case class ExternalMetric(external: ExternalMetricSource) extends Metric {
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
                                  targetAverageUtilization: Option[Int],
                                  targetAverageValue: Option[Resource.Quantity])

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

  case class Status(observedGeneration: Option[Int],
                    lastScaleTime: Option[Timestamp],
                    currentReplicas: Int,
                    desiredReplicas: Int,
                    currentMetrics: List[MetricStatus],
                    conditions: List[Condition])

  object Spec {
    def apply(apiVersion: String, kind: String, name: String): Spec = {
      new Spec(CrossVersionObjectReference(apiVersion, kind, name))
    }
  }

  case class Spec(scaleTargetRef: CrossVersionObjectReference,
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

    def withMinReplicas(replicas: Int): Spec = {
      this.copy(minReplicas = Some(replicas))
    }

    def withMaxReplicas(replicas: Int): Spec = {
      this.copy(maxReplicas = replicas)
    }
  }

  import play.api.libs.functional.syntax._
  import play.api.libs.json.{Format, JsPath}
  import skuber.json.format._

  implicit val crossVersionObjectReferenceFmt: Format[CrossVersionObjectReference] = Json.format[CrossVersionObjectReference]
  implicit val conditionFmt: Format[Condition] = Json.format[Condition]
  implicit val limitRangeItemTypeFmt: Format[LimitRange.ItemType.Type] = Json.formatEnum(LimitRange.ItemType)
  implicit val metricsSourceTypeFmt: Format[MetricsSourceType.Value] = Json.formatEnum(MetricsSourceType)

  implicit val resourceMetricStatusFmt: Format[ResourceMetricStatus] = Json.format[ResourceMetricStatus]

  implicit val objectMetricStatusFmt: Format[ObjectMetricStatus] = ((JsPath \ "target").format[CrossVersionObjectReference] and
    (JsPath \ "metricName").format[String] and
    (JsPath \ "currentValue").format[Resource.Quantity] and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "averageValue").formatNullable[Resource.Quantity]) (ObjectMetricStatus.apply,
    om => (om.target, om.metricName, om.currentValue, om.selector, om.averageValue))

  implicit val podsMetricStatusFmt: Format[PodsMetricStatus] = ((JsPath \ "metricName").format[String] and
    (JsPath \ "currentAverageValue").format[Resource.Quantity] and
    (JsPath \ "selector").formatNullableLabelSelector) (PodsMetricStatus.apply, p => (p.metricName, p.currentAverageValue, p.selector))

  implicit val externalMetricStatusFmt: Format[ExternalMetricStatus] = ((JsPath \ "metricName").format[String] and
    (JsPath \ "metricSelector").formatNullableLabelSelector and
    (JsPath \ "currentValue").formatNullable[Resource.Quantity] and
    (JsPath \ "currentAverageValue").formatNullable[Resource.Quantity]) (ExternalMetricStatus.apply, e => (e.metricName, e.metricSelector, e.currentValue, e.currentAverageValue))


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

  implicit val depStatusFmt: Format[Status] = ((JsPath \ "observedGeneration").formatNullable[Int] and
    (JsPath \ "lastScaleTime").formatNullable[Timestamp] and
    (JsPath \ "currentReplicas").format[Int] and
    (JsPath \ "desiredReplicas").format[Int] and
    (JsPath \ "currentMetrics").formatMaybeEmptyList[MetricStatus] and
    (JsPath \ "conditions").formatMaybeEmptyList[Condition]) (Status.apply,
    s => (s.observedGeneration, s.lastScaleTime, s.currentReplicas, s.desiredReplicas, s.currentMetrics, s.conditions))

  implicit val resourceMetricSourceFmt: Format[ResourceMetricSource] = Json.format[ResourceMetricSource]

  implicit val objectMetricSourceFmt: Format[ObjectMetricSource] = ((JsPath \ "target").format[CrossVersionObjectReference] and
    (JsPath \ "metricName").format[String] and
    (JsPath \ "targetValue").format[Resource.Quantity] and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "averageValue").formatNullable[Resource.Quantity]) (ObjectMetricSource.apply,
    o => (o.target, o.metricName, o.targetValue, o.selector, o.averageValue))

  implicit val podsMetricSourceFmt: Format[PodsMetricSource] = ((JsPath \ "metricName").format[String] and
    (JsPath \ "targetAverageValue").format[Resource.Quantity] and
    (JsPath \ "selector").formatNullableLabelSelector) (PodsMetricSource.apply, p => (p.metricName, p.targetAverageValue, p.selector))

  implicit val externalMetricSourceFmt: Format[ExternalMetricSource] = ((JsPath \ "metricName").format[String] and
    (JsPath \ "metricSelector").formatNullableLabelSelector and
    (JsPath \ "targetValue").formatNullable[Resource.Quantity] and
    (JsPath \ "targetAverageValue").formatNullable[Resource.Quantity]) (ExternalMetricSource.apply,
    e => (e.metricName, e.metricSelector, e.targetValue, e.targetAverageValue))


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

  implicit val depSpecFmt: Format[Spec] = ((JsPath \ "scaleTargetRef").format[CrossVersionObjectReference] and
    (JsPath \ "minReplicas").formatNullable[Int] and
    (JsPath \ "maxReplicas").format[Int] and
    (JsPath \ "metrics").formatMaybeEmptyList[Metric]) (Spec.apply, s => (s.scaleTargetRef, s.minReplicas, s.maxReplicas, s.metrics))

  implicit lazy val horizontalPodAutoscalerFormat: Format[HorizontalPodAutoscaler] = (objFormat and
    (JsPath \ "spec").formatNullable[Spec] and
    (JsPath \ "status").formatNullable[Status]) (HorizontalPodAutoscaler.apply, h => (h.kind, h.apiVersion, h.metadata, h.spec, h.status))

  implicit val horizontalPodAutoscalerListFormat: Format[HorizontalPodAutoscalerList] = ListResourceFormat[HorizontalPodAutoscaler]
}