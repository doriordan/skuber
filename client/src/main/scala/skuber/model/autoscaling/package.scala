package skuber.model

/**
  * @author David O'Riordan
  */
package object autoscaling {
  type HorizontalPodAutoscalerList=ListResource[HorizontalPodAutoscaler]
  case class CPUTargetUtilization(targetPercentage: Int)
}
