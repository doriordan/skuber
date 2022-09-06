package skuber.apiextensions

import play.api.libs.json.{JsPath, JsResult, JsSuccess, JsValue, OFormat}
import skuber.ResourceSpecification.StatusSubresource
import skuber.json.format.EnumFormatter
import skuber.{NonCoreResourceSpecification, ObjectEditor, ObjectMeta, ObjectResource, ResourceDefinition, ResourceSpecification, TypeMeta, apiextensions}

/**
 * @author David O'Riordan
 */
case class CustomResourceDefinition(val kind: String = "CustomResourceDefinition",
                                    override val apiVersion: String = "apiextensions.k8s.io/v1beta1",
                                    val metadata: ObjectMeta,
                                    spec: CustomResourceDefinition.Spec) extends ObjectResource

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

  val crdNames = Names("customresourcedefinitions",
    "customresourcedefinition",
    "CustomResourceDefinition",
    List("crd"))

  val specification: Spec = NonCoreResourceSpecification(apiGroup = "apiextensions.k8s.io",
    version = "v1beta1",
    scope = Scope.Cluster,
    names = crdNames)

  def apply(name: String,
            kind: String): CustomResourceDefinition = CustomResourceDefinition(name, kind, "v1", Scope.Namespaced, None, Nil)

  def apply(name: String,
            kind: String,
            scope: Scope.Value): CustomResourceDefinition = CustomResourceDefinition(name, kind, "v1", scope, None, Nil)

  def apply(name: String,
            kind: String,
            shortNames: List[String]): CustomResourceDefinition = CustomResourceDefinition(name, kind, "v1", Scope.Namespaced, None, shortNames)

  def apply(name: String,
            kind: String,
            scope: Scope.Value,
            shortNames: List[String]): CustomResourceDefinition = CustomResourceDefinition(name, kind, "v1", scope, None, shortNames)

  def apply(name: String,
            kind: String,
            version: String,
            scope: Scope.Value,
            singular: Option[String],
            shortNames: List[String]): CustomResourceDefinition = {
    val nameParts = name.split('.')
    if (nameParts.length < 2)
      throw new Exception("name must be of format <plural>.<group>>")
    val plural = nameParts.head
    val group = nameParts.tail.mkString(".")

    val names = ResourceSpecification.Names(plural = plural, kind = kind, singular = singular.getOrElse(""), shortNames = shortNames)
    val spec = Spec(apiGroup = group, version = version, names = names, scope = scope)
    CustomResourceDefinition(metadata = ObjectMeta(name = name), spec = spec)
  }

  def apply[T <: TypeMeta : ResourceDefinition]: CustomResourceDefinition = {
    val crdSpec: Spec = try {
      implicitly[ResourceDefinition[T]].spec.asInstanceOf[Spec]
    } catch {
      case ex: ClassCastException =>
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

  import play.api.libs.json.{Json, Format}
  import play.api.libs.functional.syntax._

  import skuber.json.format.{maybeEmptyFormatMethods, objectMetaFormat}

  implicit val scopeFormat: Format[ResourceSpecification.Scope.Value] = Json.formatEnum(Scope)
  implicit val namesFormat: OFormat[apiextensions.CustomResourceDefinition.Names] = ((JsPath \ "plural").format[String] and
    (JsPath \ "singular").format[String] and
    (JsPath \ "kind").format[String] and
    (JsPath \ "shortNames").formatMaybeEmptyList[String] and
    (JsPath \ "listKind").formatNullable[String] and
    (JsPath \ "categories").formatMaybeEmptyList[String]) (Names.apply,
    names => (names.plural, names.singular, names.kind, names.shortNames, names.listKind, names.categories))

  implicit val versionFormat: Format[ResourceSpecification.Version] = ((JsPath \ "name").format[String] and
    (JsPath \ "served").formatMaybeEmptyBoolean() and
    (JsPath \ "storage").formatMaybeEmptyBoolean()) (ResourceSpecification.Version.apply,
    res => (res.name, res.served, res.storage))

  implicit val scaleSubresourceFmt: Format[ScaleSubresource] = Json.format[ScaleSubresource]
  implicit val statusSubResourceFmt: Format[StatusSubresource] = new Format[StatusSubresource] {
    override def writes(o: StatusSubresource): JsValue = Json.obj()

    override def reads(json: JsValue): JsResult[StatusSubresource] = JsSuccess(StatusSubresource())
  }
  implicit val subresourcesFmt: Format[Subresources] = Json.format[Subresources]

  implicit val crdSpecFmt: Format[Spec] = ((JsPath \ "group").format[String] and
    (JsPath \ "version").formatNullable[String] and
    (JsPath \ "versions").formatMaybeEmptyList[Version] and
    new EnumFormatter(JsPath \ "scope").formatEnum(Scope) and
    (JsPath \ "names").format[Names] and
    (JsPath \ "subresources").formatNullable[Subresources]) (CustomResourceDefinition.Spec.apply,
    crd => (crd.apiGroup, crd.version, crd.versions, crd.scope, crd.names, crd.subresources))

  implicit val crdFmt: Format[CustomResourceDefinition] = ((JsPath \ "kind").format[String] and
    (JsPath \ "apiVersion").format[String] and
    (JsPath \ "metadata").format[ObjectMeta] and
    (JsPath \ "spec").format[Spec]) (CustomResourceDefinition.apply, crd => (crd.kind, crd.apiVersion, crd.metadata, crd.spec))


}