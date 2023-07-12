
package skuber.autoscaling

import skuber.ListResource

package object v2 {
  val autoscalingAPIVersion = "autoscaling/v2"
  type HorizontalPodAutoscalerList = ListResource[skuber.autoscaling.v2.HorizontalPodAutoscaler]
}
