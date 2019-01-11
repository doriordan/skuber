
package skuber.autoscaling

import skuber.ListResource

package object v2beta1 {
  val autoscalingAPIVersion = "autoscaling/v2beta1"
  type HorizontalPodAutoscalerList = ListResource[skuber.autoscaling.v2beta1.HorizontalPodAutoscaler]
}
