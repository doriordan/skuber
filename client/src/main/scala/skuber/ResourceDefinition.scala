package skuber

import play.api.libs.json.{Format, Json}
import skuber.apiextensions.CustomResourceDefinition

import scala.reflect.runtime.universe._

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
    subresources: Option[ResourceSpecification.Subresources] = None
  ): ResourceDefinition[T] =
  {
    val singularStr=singular.getOrElse(kind.toLowerCase)
    val pluralStr=plural.getOrElse(s"${singularStr}s")
    val names=ResourceSpecification.Names(plural=pluralStr, singular = singularStr, kind = kind, shortNames = shortNames)
    val defSpec=NonCoreResourceSpecification(group,Some(version), Nil, scope, names, subresources)
    new ResourceDefinition[T]{ override def spec= defSpec }
  }

  /*
   * Generate a ResourceDefinition for a specific CustomResource type from the associated CRD
   */
  def apply[T <: TypeMeta](crd: CustomResourceDefinition): ResourceDefinition[T] = {
    new ResourceDefinition[T] {
      override def spec: ResourceSpecification = crd.spec
    }
  }

  /**
    * This will provide an implicit resource definition require by API `list` methods, as long as an implicit resource
    * definition for the corresponding object resource type is in scope
    * @param rd
    * @tparam O
    * @return
    */
  implicit def listDef[O <: ObjectResource](implicit rd: ResourceDefinition[O]): ResourceDefinition[ListResource[O]] = {
    new ResourceDefinition[ListResource[O]] {
      override def spec: ResourceSpecification = rd.spec
    }
  }
}