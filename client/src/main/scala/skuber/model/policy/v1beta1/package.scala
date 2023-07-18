package skuber.model.policy

import skuber.model.ListResource

package object v1beta1 {
  val policyAPIVersion = "policy/v1beta1"
  type PodDisruptionBudgetList = ListResource[PodDisruptionBudget]
}
