
package skuber.model.autoscaling

import skuber.model.ListResource

package object v2 {
  val autoscalingAPIVersion = "autoscaling/v2"
  type HorizontalPodAutoscalerList = ListResource[HorizontalPodAutoscaler]
}
