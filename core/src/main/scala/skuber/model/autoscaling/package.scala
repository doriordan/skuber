package skuber.model

import skuber.model.autoscaling.v1.HorizontalPodAutoscaler

/**
  * @author David O'Riordan
  */
package object autoscaling {
  type HorizontalPodAutoscalerList=ListResource[HorizontalPodAutoscaler]
  case class CPUTargetUtilization(targetPercentage: Int)
}
