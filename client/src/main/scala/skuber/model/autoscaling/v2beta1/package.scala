
package skuber.model.autoscaling

import skuber.model.ListResource

package object v2beta1 {
  val autoscalingAPIVersion = "autoscaling/v2beta1"
  type HorizontalPodAutoscalerList = ListResource[HorizontalPodAutoscaler]
}
