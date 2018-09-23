package skuber.policy

import skuber.ListResource

package object v1beta1 {
  val policyAPIVersion = "policy/v1beta1"
  type PodDisruptionBudgetList = ListResource[skuber.policy.v1beta1.PodDisruptionBudget]
}
