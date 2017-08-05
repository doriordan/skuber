package skuber

/**
  * @author David O'Riordan
  *         Maintain API-relevant details for a resource type in Kubernetes
  *         Each resource type O (e.g. Pod) implemented in skuber defines an implicit value of type
  *         ResourceDefinition[O] in its companion object which has a method to return an ResourceSpecification.
  *         When a client invokes a skuber API method on a resource of type O then that value gets implicitly
  *         passed to the method, which provides skuber with the details required to set the URL for the remote call.
  *         ResourceSpecification mirrors the specification of the CustomResourceDefinition type
  *         introduced in Kubernetes V1.7, and the CRD case class utilises it for that. It is an abstract
  *         base class with two concrete case subclasses, for core and non-core API group resource types respectively.
  */

abstract class ResourceSpecification {
  def apiPathPrefix: String

  def group: Option[String] // set to None if defined on core API group, otherwise Some(groupName)

  def version: String

  def scope: ResourceSpecification.Scope.Value

  def names: ResourceSpecification.Names
}

object ResourceSpecification {

  object Scope extends Enumeration {
    type ResourceScope = Value
    val Namespaced, Cluster = Value
  }

  case class Names(
    plural: String,
    singular: String,
    kind: String,
    shortNames: List[String]  // these abbreviations are only really useful at the moment with CRDs
  )
}

case class CoreResourceSpecification(
  override val group: Option[String] = None,
  override val version: String = "v1",
  override val scope: ResourceSpecification.Scope.Value,
  override val names: ResourceSpecification.Names) extends ResourceSpecification
{
  def apiPathPrefix="api"
}

case class NonCoreResourceSpecification(
  override val group: Option[String],
  override val version: String,
  override val scope: ResourceSpecification.Scope.Value,
  override val names: ResourceSpecification.Names) extends ResourceSpecification
{
  def apiPathPrefix="apis"
}


