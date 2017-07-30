package skuber.apiextensions

import skuber.{ResourceSpecification, NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition}
import skuber.ResourceSpecification.{Names, Scope}

/**
  * @author David O'Riordan
  */
case class CustomResourceDefinition(
  val kind: String = "CustomResourceDefinition",
  override val apiVersion: String = "apiextensions.k8s.io/v1beta1",
  val metadata: ObjectMeta,
  spec: ResourceSpecification
) extends ObjectResource

object CustomResourceDefinition {
  val crdNames=Names("customresourcedefinitions","customresourcedefinition","CustomResourceDefinition", List("crd"))
  val specification = NonCoreResourceSpecification (
    group=Some("apiextensions.k8s.io"),
    version="v1beta1",
    scope = Scope.Clustered,
    names=crdNames)
  implicit val crdDef = new ResourceDefinition[CustomResourceDefinition] { def spec=specification }
  implicit val crdListDef = new ResourceDefinition[CustomResourceDefinitionList] { def spec=specification }
}