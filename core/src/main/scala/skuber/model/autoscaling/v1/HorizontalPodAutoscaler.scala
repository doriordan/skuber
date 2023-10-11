package skuber.model.autoscaling.v1

import play.api.libs.json.{Format, Json}
import skuber.model.ResourceSpecification._
import skuber.model._
import skuber.model.apps.v1.Deployment
import skuber.model.autoscaling.{CPUTargetUtilization, HorizontalPodAutoscalerList}

/**
 * @author David O'Riordan
 * Note: this supports the original "autoscaling/v1" version of HPAS , which has CPU utilisation
 * as a target metric.
 * Support for the newer "v2beta1" version , introduced in k8s v1.6 and which supports memory
 * and custom metrics, is not yet done
 *
 */
case class HorizontalPodAutoscaler(
  kind: String ="HorizontalPodAutoscaler",
  apiVersion: String =  "autoscaling/v1",
  metadata: ObjectMeta,
  spec: HorizontalPodAutoscaler.Spec,
  status: Option[HorizontalPodAutoscaler.Status] = None)
    extends ObjectResource
{

  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

  def withMinReplicas(min: Int) = this.copy(spec=this.spec.copy(minReplicas=Some(min)))
  def withMaxReplicas(max: Int) = this.copy(spec=this.spec.copy(maxReplicas=max))
  def withCPUTargetUtilization(cpu: Int) =
    this.copy(spec=this.spec.copy(cpuUtilization=Some(CPUTargetUtilization(cpu))))
}
      
object HorizontalPodAutoscaler {

  val specification=NonCoreResourceSpecification(
    apiGroup = "autoscaling",
    version = "v1",
    scope = Scope.Namespaced,
    names = Names(
      plural = "horizontalpodautoscalers",
      singular = "horizontalpodautoscaler",
      kind = "HorizontalPodAutoscaler",
      shortNames = List("hpa")
    )
  )
  implicit val hpasDef: ResourceDefinition[HorizontalPodAutoscaler] = new ResourceDefinition[HorizontalPodAutoscaler] { def spec=specification }
  implicit val hpasListDef: ResourceDefinition[HorizontalPodAutoscalerList] = new ResourceDefinition[HorizontalPodAutoscalerList] { def spec=specification }

  def scale(rc: ReplicationController): HorizontalPodAutoscaler = 
    build(rc.name, rc.metadata.namespace, "ReplicationController")

  def scale(de: Deployment): HorizontalPodAutoscaler =
    build(de.name, de.metadata.namespace, "Deployment")
   
  def build(name: String, namespace: String, kind: String, apiVersion: String = "v1") : HorizontalPodAutoscaler = {
    val meta = ObjectMeta(name=name,namespace=namespace)
    val scaleTargetRef = CrossVersionObjectReference(
        kind=kind,
        name=name,
        apiVersion=apiVersion)
    HorizontalPodAutoscaler(metadata=meta,spec=Spec(scaleTargetRef=scaleTargetRef))
  }

  case class Spec(
    scaleTargetRef: CrossVersionObjectReference,
    minReplicas: Option[Int] = Some(1),
    maxReplicas: Int = 1,
    cpuUtilization: Option[CPUTargetUtilization] = None
  )
  case class CrossVersionObjectReference(
    apiVersion: String = "autoscaling/v1",
    kind: String = "CrossVersionObjectReference",
    name: String
  )
  case class Status(
    observedGeneration: Option[Long] = None,
    lastScaleTime: Option[Timestamp] = None,
    currentReplicas: Int = 0,
    desiredReplicas: Int = 0,
    currentCPUUtilizationPercentage: Option[Int] = None)

  object MetricsSourceType extends Enumeration {
    type MetricsSourceType = Value
    val Object, Pods, Resource, External = Value
  }

  import skuber.json.format._

  // HorizontalPodAutoscaler Json formatters
  implicit val cpuTUFmt: Format[CPUTargetUtilization] = Json.format[CPUTargetUtilization]
  implicit val cvObjRefFmt: Format[CrossVersionObjectReference] = Json.format[CrossVersionObjectReference]
  implicit val hpasSpecFmt: Format[HorizontalPodAutoscaler.Spec] = Json.format[HorizontalPodAutoscaler.Spec]
  implicit val hpasStatusFmt: Format[HorizontalPodAutoscaler.Status] = Json.format[HorizontalPodAutoscaler.Status]
  implicit val hpasFmt: Format[HorizontalPodAutoscaler] =  Json.format[HorizontalPodAutoscaler]
}