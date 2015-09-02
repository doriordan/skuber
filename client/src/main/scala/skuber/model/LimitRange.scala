package skuber.model

import coretypes._

/**
 * @author David O'Riordan
 */
case class LimitRange (
    val kind: String ="LimitRange",
    override val apiVersion: String = "v1",
    val metadata: ObjectMeta = ObjectMeta(),
    spec: Option[LimitRange.Spec] = None)
      extends ObjectResource with KListItem
   
object LimitRange {
  case class Spec(items: List[Item] = List())
  
  object ItemType extends Enumeration {
      type Type = Value
      val Pod, Container = Value
  }
  
  import Resource.ResourceList
  case class Item(
      _type: ItemType.Type = ItemType.Pod,
      max: ResourceList = Map(),
      min: ResourceList = Map(),
      default: ResourceList = Map(),
      defaultRequest: ResourceList = Map(),
      maxLimitRequestRation: ResourceList = Map())
}