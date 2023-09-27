package skuber.apiextensions.v1

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.json.format.{enumFormatMethods, maybeEmptyFormatMethods, objectMetaFormat}
import skuber.ResourceSpecification.{Schema, StatusSubresource}
import skuber.{ListResource, NonCoreResourceSpecification, ObjectEditor, ObjectMeta, ObjectResource, ResourceDefinition, ResourceSpecification, TypeMeta}

final case class CustomResourceDefinition(
  kind: String = "CustomResourceDefinition",
  override val apiVersion: String = "apiextensions.k8s.io/v1",
  metadata: ObjectMeta,
  spec: CustomResourceDefinition.Spec
) extends ObjectResource

object CustomResourceDefinition {

  type Spec = NonCoreResourceSpecification
  val Spec: NonCoreResourceSpecification.type = NonCoreResourceSpecification

  val Scope: ResourceSpecification.Scope.type = ResourceSpecification.Scope

  type Names = ResourceSpecification.Names
  val Names: ResourceSpecification.Names.type = ResourceSpecification.Names

  type Version = ResourceSpecification.Version

  type Subresources = ResourceSpecification.Subresources
  type ScaleSubresource = ResourceSpecification.ScaleSubresource
  type StatusSubresource = ResourceSpecification.StatusSubresource

  type CustomResourceDefinitionList = ListResource[CustomResourceDefinition]

  val crdNames = Names("customresourcedefinitions", "customresourcedefinition", "CustomResourceDefinition", List("crd"))

  val specification =
    NonCoreResourceSpecification(apiGroup = "apiextensions.k8s.io", version = "v1", scope = Scope.Cluster, names = crdNames)

  def apply(name: String, kind: String): CustomResourceDefinition =
    CustomResourceDefinition(name, kind, "v1", Scope.Namespaced, None, Nil, Nil)

  def apply(name: String, kind: String, scope: Scope.Value): CustomResourceDefinition =
    CustomResourceDefinition(name, kind, "v1", scope, None, Nil, Nil)

  def apply(name: String, kind: String, shortNames: List[String]): CustomResourceDefinition =
    CustomResourceDefinition(name, kind, "v1", Scope.Namespaced, None, shortNames, Nil)

  def apply(name: String, kind: String, scope: Scope.Value, shortNames: List[String], versions: List[Version]): CustomResourceDefinition =
    CustomResourceDefinition(name, kind, "v1", scope, None, shortNames, versions)

  def apply(
    name: String,
    kind: String,
    version: String,
    scope: Scope.Value,
    singular: Option[String],
    shortNames: List[String],
    versions: List[Version]
  ): CustomResourceDefinition = {
    if (versions.exists(v => v.schema.isEmpty))
      throw new Exception("Schema must be specified in CRD v1 version")

    val nameParts = name.split('.')
    if (nameParts.length < 2)
      throw new Exception("Name must be of format <plural>.<group>>")

    val plural = nameParts.head
    val group = nameParts.tail.mkString(".")

    val names = ResourceSpecification.Names(plural = plural, kind = kind, singular = singular.getOrElse(""), shortNames = shortNames)
    val spec = Spec(apiGroup = group, version = Some(version), versions = Some(versions), scope = scope, names = names, subresources = None)
    CustomResourceDefinition(metadata = ObjectMeta(name = name), spec = spec)
  }

  def apply[T <: TypeMeta : ResourceDefinition]: CustomResourceDefinition = {
    val crdSpec: Spec =
      try
        implicitly[ResourceDefinition[T]].spec.asInstanceOf[Spec]
      catch {
        case _: ClassCastException =>
          val msg = "Requires an implicit resource definition that has a NonCoreResourceSpecification"
          throw new skuber.K8SException(skuber.api.client.Status(message = Some(msg)))
      }
    val name = s"${crdSpec.names.plural}.${crdSpec.group.get}"
    new CustomResourceDefinition(metadata = ObjectMeta(name = name), spec = crdSpec)
  }

  implicit val crdDef: ResourceDefinition[CustomResourceDefinition] = new ResourceDefinition[CustomResourceDefinition] {
    def spec: ResourceSpecification = specification
  }
  implicit val crdListDef: ResourceDefinition[CustomResourceDefinitionList] = new ResourceDefinition[CustomResourceDefinitionList] {
    def spec: ResourceSpecification = specification
  }

  implicit val crdEditor: ObjectEditor[CustomResourceDefinition] = new ObjectEditor[CustomResourceDefinition] {
    override def updateMetadata(obj: CustomResourceDefinition, newMetadata: ObjectMeta): CustomResourceDefinition = obj.copy(metadata = newMetadata)
  }

  // json formatters for sending/receiving CRD resources
  implicit val scopeFormat: Format[ResourceSpecification.Scope.Value] = Json.formatEnum(Scope)

  implicit val namesFormat: OFormat[CustomResourceDefinition.Names] = (
    (JsPath \ "plural").format[String] and
      (JsPath \ "singular").format[String] and
      (JsPath \ "kind").format[String] and
      (JsPath \ "shortNames").formatMaybeEmptyList[String] and
      (JsPath \ "listKind").formatNullable[String] and
      (JsPath \ "categories").formatMaybeEmptyList[String]
    )(Names.apply, names => (names.plural, names.singular, names.kind, names.shortNames, names.listKind, names.categories))

  implicit val scaleSubresourceFmt: Format[ScaleSubresource] = Json.format[ScaleSubresource]

  implicit val statusSubResourceFmt: Format[StatusSubresource] = new Format[StatusSubresource] {
    override def writes(o: StatusSubresource): JsValue = Json.obj()
    override def reads(json: JsValue): JsResult[StatusSubresource] = JsSuccess(StatusSubresource())
  }

  implicit val subresourcesFmt: Format[Subresources] = Json.format[Subresources]

  implicit val schemaFormat: Format[Schema] = Json.format[Schema]

  implicit val versionFormat: Format[ResourceSpecification.Version] = (
    (JsPath \ "name").format[String] and
      (JsPath \ "served").formatMaybeEmptyBoolean() and
      (JsPath \ "storage").formatMaybeEmptyBoolean() and
      (JsPath \ "schema").formatNullable[Schema] and
      (JsPath \ "subresources").formatNullable[Subresources]
    )(ResourceSpecification.Version.apply, res => (res.name, res.served, res.storage, res.schema, res.subresources))

  implicit val crdSpecFmt: Format[Spec] = (
    (JsPath \ "group").format[String] and
      (JsPath \ "version").formatNullable[String] and
      (JsPath \ "versions").formatNullable[List[Version]] and
      (JsPath \ "scope").formatEnum(Scope) and
      (JsPath \ "names").format[Names] and
      (JsPath \ "subresources").formatNullable[Subresources]
    )(Spec.apply, crd => (crd.apiGroup, crd.version, crd.versions, crd.scope, crd.names, crd.subresources))

  implicit val crdFmt: Format[CustomResourceDefinition] = (
    (JsPath \ "kind").format[String] and
      (JsPath \ "apiVersion").format[String] and
      (JsPath \ "metadata").format[ObjectMeta] and
      (JsPath \ "spec").format[Spec]
    )(CustomResourceDefinition.apply, crd => (crd.kind, crd.apiVersion, crd.metadata, crd.spec))
}
