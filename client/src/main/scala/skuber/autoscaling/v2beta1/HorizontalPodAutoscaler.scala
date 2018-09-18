package skuber.autoscaling.v2beta1

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition, Scale}

case class HorizontalPodAutoscaler(override val kind: String = "HorizontalPodAutoscaler",
                                   override val apiVersion: String = autoscalingAPIVersion,
                                   metadata: ObjectMeta,
                                   spec: Option[HorizontalPodAutoscaler.Spec] = None,
                                   status: Option[HorizontalPodAutoscaler.Status] = None) extends ObjectResource {

}

object HorizontalPodAutoscaler {
  def apply(name: String): HorizontalPodAutoscaler = {
    HorizontalPodAutoscaler(metadata = ObjectMeta(name = name))
  }

  val specification = NonCoreResourceSpecification(
    apiGroup = "autoscaling",
    version = "v2beta1",
    scope = Scope.Namespaced,
    names = Names(
      plural = "horizontalpodautoscalers",
      singular = "horizontalpodautoscaler",
      kind = "HorizontalPodAutoscaler",
      shortNames = List("hpa")
    )
  )
  implicit val stsDef: ResourceDefinition[HorizontalPodAutoscaler] = new ResourceDefinition[HorizontalPodAutoscaler] {
    def spec: NonCoreResourceSpecification = specification
  }

  implicit val stsListDef: ResourceDefinition[HorizontalPodAutoscalerList] = new ResourceDefinition[HorizontalPodAutoscalerList] {
    def spec: NonCoreResourceSpecification = specification
  }

  case class Spec()

  case class Status()
}