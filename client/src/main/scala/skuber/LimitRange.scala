package skuber


/**
 * @author David O'Riordan
 */
case class LimitRange (
    val kind: String ="LimitRange",
    override val apiVersion: String = v1,
    val metadata: ObjectMeta = ObjectMeta(),
    spec: Option[LimitRange.Spec] = None)
      extends ObjectResource {
  
  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

}
   
object LimitRange {

  val specification=CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(
      plural="limitranges",
      singular="limitrange",
      kind="LimitRange",
      shortNames=List("limits")
    )
  )
  implicit val lrDef = new ResourceDefinition[LimitRange] { def spec=specification }
  implicit val lrListDef = new ResourceDefinition[LimitRangeList] { def spec=specification }

  case class Spec(items: List[Item] = List())
  
  object ItemType extends Enumeration {
      type Type = Value
      val Pod, Container = Value
  }
  
  import Resource.ResourceList
  case class Item(
      _type: Option[ItemType.Type] = None,
      max: ResourceList = Map(),
      min: ResourceList = Map(),
      default: ResourceList = Map(),
      defaultRequest: ResourceList = Map(),
      maxLimitRequestRation: ResourceList = Map())
}