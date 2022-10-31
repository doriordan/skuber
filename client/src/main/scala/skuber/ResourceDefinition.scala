package skuber

import skuber.ResourceSpecification.Subresources
import skuber.apiextensions.v1beta1.{CustomResourceDefinition => v1beta1CRD}
import skuber.apiextensions.v1.{CustomResourceDefinition => v1CRD}

/**
  * @author David O'Riordan
  *
  *  An implicit ResourceDefinition must be provided for every Kubernetes resource type accessed via skuber - the
  *  client API uses the details in the supplied implicit to determine key details for constructing requests such as URL
  *  path components.
  *  skuber defines this implicit value in the companion object of each kind in the model that it supports so it will
  *  automatically be imported with the model.
  */
trait ResourceDefinition[T <: TypeMeta] {
  def spec: ResourceSpecification
}

object ResourceDefinition {

  def apply[T <: TypeMeta](rspec: ResourceSpecification) = new ResourceDefinition[T] {
    def spec = rspec
  }

  /*
   * Generate a ResourceDefinition for a specific type from specified definition fields, or falling back to defaults
   * (such as class name for kind and reverse package name for group) where not specified.
   */
  def apply[T <: TypeMeta](
    kind: String,
    group: String,
    version: String = "v1",
    singular: Option[String] = None,
    plural: Option[String] = None,
    scope: ResourceSpecification.Scope.Value = ResourceSpecification.Scope.Namespaced,
    shortNames: List[String] = Nil,
    versions: List[ResourceSpecification.Version] = Nil,
    subresources: Option[Subresources] = None
  ): ResourceDefinition[T] =
  {
    val singularStr=singular.getOrElse(kind.toLowerCase)
    val pluralStr=plural.getOrElse(s"${singularStr}s")
    val names=ResourceSpecification.Names(plural=pluralStr, singular = singularStr, kind = kind, shortNames = shortNames)
    val defSpec=NonCoreResourceSpecification(group, Some(version), scope, names, Some(versions), subresources)
    new ResourceDefinition[T]{ override def spec= defSpec }
  }

  /*
   * Generate a ResourceDefinition for a specific CustomResource type from the associated beta CRD
   */
  @deprecated("Pass a skuber.apiextensions.v1.CustomResourceDefinition instance")
  def apply[T <: TypeMeta](crd: v1beta1CRD): ResourceDefinition[T] = {
    new ResourceDefinition[T] {
      override def spec: ResourceSpecification = crd.spec
    }
  }

  /*
   * Generate a ResourceDefinition for a specific CustomResource type from the associated v1 CRD
   */
  def apply[T <: TypeMeta](crd: v1CRD): ResourceDefinition[T] = {
    new ResourceDefinition[T] {
      override def spec: ResourceSpecification = crd.spec
    }
  }

  /**
    * This will provide an implicit resource definition require by API `list` methods, as long as an implicit resource
    * definition for the corresponding object resource type is in scope
    * @param rd resource definition for the object resource kind
    * @tparam O the object resource type
    * @return an implicit resource definition for the list resource kind
    */
  implicit def listDef[O <: ObjectResource](implicit rd: ResourceDefinition[O]): ResourceDefinition[ListResource[O]] = {
    new ResourceDefinition[ListResource[O]] {
      override def spec: ResourceSpecification = rd.spec
    }
  }
}
