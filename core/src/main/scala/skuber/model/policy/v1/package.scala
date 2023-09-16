package skuber.model.policy

import skuber.model.ListResource

package object v1 {
  val policyAPIVersion = "policy/v1"
  type PodDisruptionBudgetList = ListResource[PodDisruptionBudget]
}
