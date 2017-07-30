package skuber.ext

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ReplicationController, ResourceDefinition, Timestamp}

/**
 * @author David O'Riordan
 */
case class HorizontalPodAutoscaler(
  	val kind: String ="HorizontalPodAutoscaler",
  	override val apiVersion: String =  extensionsAPIVersion,
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
    group = Some("extensions"),
    version = "v1beta1",
    scope = Scope.Namespaced,
    names = Names(
      plural = "horizontalpodautoscalers",
      singular = "replicaset",
      kind = "ReplicaSet",
      shortNames = List("rs")
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
    val scaleRef = SubresourceReference(
        kind=kind,
        name=name,
        apiVersion=apiVersion,
        subresource="scale")
   HorizontalPodAutoscaler(metadata=meta,spec=Spec(scaleRef=scaleRef))     
  }
   case class Spec( 
     scaleRef: SubresourceReference,
     minReplicas: Option[Int] = Some(1),
     maxReplicas: Int = 1,
     cpuUtilization: Option[CPUTargetUtilization] = None
   )
           
   case class Status(
      observedGeneration: Option[Long] = None,
      lastScaleTime: Option[Timestamp] = None,
      currentReplicas: Int = 0,
      desiredReplicas: Int = 0,
      currentCPUUtilizationPercentage: Option[Int] = None)
}