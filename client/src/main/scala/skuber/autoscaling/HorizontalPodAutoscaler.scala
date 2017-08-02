package skuber.autoscaling

import play.api.libs.functional.syntax.unlift
import play.api.libs.json.{Format,  Json}
import skuber.ResourceSpecification.{Names, Scope}
import skuber.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ReplicationController, ResourceDefinition, Timestamp}
import skuber.json.format.objectMetaFormat

/**
 * @author David O'Riordan
 */
case class HorizontalPodAutoscaler(
  	val kind: String ="HorizontalPodAutoscaler",
  	override val apiVersion: String =  "autoscaling/v1",
    val metadata: ObjectMeta,
    spec: HorizontalPodAutoscaler.Spec,
    status: Option[HorizontalPodAutoscaler.Status] = None) 
      extends ObjectResource {

  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

  def withMinReplicas(min: Int) = this.copy(spec=this.spec.copy(minReplicas=Some(min)))
  def withMaxReplicas(max: Int) = this.copy(spec=this.spec.copy(maxReplicas=max))
  def withCPUTargetUtilization(cpu: Int) =
    this.copy(spec=this.spec.copy(cpuUtilization=Some(CPUTargetUtilization(cpu))))
}
      
object HorizontalPodAutoscaler {

  val specification=NonCoreResourceSpecification(
    group = Some("autoscaling"),
    version = "v1",
    scope = Scope.Namespaced,
    names = Names(
      plural = "horizontalpodautoscalers",
      singular = "horizontalpodautoscaler",
      kind = "HorizontalPodAutoscaler",
      shortNames = List("hpa")
    )
  )
  implicit val hpasDef = new ResourceDefinition[HorizontalPodAutoscaler] { def spec=specification }
  implicit val hpasListDef = new ResourceDefinition[HorizontalPodAutoscalerList] { def spec=specification }

  def scale(rc: ReplicationController): HorizontalPodAutoscaler = 
    build(rc.name, rc.metadata.namespace, "ReplicationController")

  def scale(de: skuber.apps.Deployment): HorizontalPodAutoscaler =
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

  // HorizontalPodAutoscaler Json formatters
  implicit val cpuTUFmt: Format[CPUTargetUtilization] = Json.format[CPUTargetUtilization]
  implicit val cvObjRefFmt: Format[CrossVersionObjectReference] = Json.format[CrossVersionObjectReference]
  implicit val hpasSpecFmt: Format[HorizontalPodAutoscaler.Spec] = Json.format[HorizontalPodAutoscaler.Spec]
  implicit val hpasStatusFmt: Format[HorizontalPodAutoscaler.Status] = Json.format[HorizontalPodAutoscaler.Status]
  implicit val hpasFmt: Format[HorizontalPodAutoscaler] =  Json.format[HorizontalPodAutoscaler]
}