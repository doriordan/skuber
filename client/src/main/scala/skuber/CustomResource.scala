package skuber

import play.api.libs.json._
import skuber.api.client.Status
import skuber.json.format.{ListResourceFormat, objFormat}

/*
 * CustomResource provides a generic model that can be used for custom resource types that follow the standard Kubernetes
 * pattern of being composed of "spec" and "status" subobjects.
 */
case class CustomResource[Sp, St](override val kind: String,
                                  override val apiVersion: String,
                                  override val metadata: ObjectMeta,
                                  spec: Sp,
                                  status: Option[St]) extends ObjectResource {
  def withMetadata(metadata: ObjectMeta) = this.copy(metadata = metadata)

  def withName(name: String) = withMetadata(metadata.copy(name = name))

  def withGenerateName(generateName: String) = withMetadata((metadata.copy(generateName = generateName)))

  def withNamespace(namespace: String) = withMetadata(metadata.copy(namespace = namespace))

  def withLabels(labels: Tuple2[String, String]*) = withMetadata(metadata.copy(labels = Map(labels: _*)))

  def withAnnotations(annotations: Tuple2[String, String]*) = withMetadata(metadata.copy(annotations = Map(annotations: _*)))

  def withFinalizers(finalizers: String*) = withMetadata(metadata.copy(finalizers = Some(finalizers.toList)))

  def withStatus(status: St): CustomResource[Sp, St] = this.copy(status = Some(status))
}

object CustomResource {

  /*
   * Make a new CustomResource of a specific type. The kind and apiVersion will be copied from the associated resource
   * definition.
   */
  def apply[Sp, St](spec: Sp)(implicit rd: ResourceDefinition[CustomResource[Sp, St]]) = new CustomResource[Sp, St](kind = rd.spec.names.kind,
    apiVersion = s"${rd.spec.group.get}/${rd.spec.defaultVersion}",
    metadata = ObjectMeta(),
    spec = spec,
    status = None)

  /**
   * Returns a value that can be passed as the required implicit parameter to the 'getStatus' and 'updateStatus' method for the given CR type
   * Requires the status subresource to be defined on the custom resource definition for the type
   *
   * @param rd The resource definition for the type - the status subresource must be defined on it
   * @tparam C The specific CustomResource type for which the status methods should be enabled
   * @return HasStatusResource value that can be passed implicitly to the `updateStatus` method for this type
   */
  def statusMethodsEnabler[C <: CustomResource[_, _]](implicit rd: ResourceDefinition[C]): HasStatusSubresource[C] = {
    if (!rd.spec.subresources.map(_.status).isDefined)
      throw new K8SException(Status(message = Some("Status subresource must be defined on the associated resource definition before status methods can be enabled")))
    new HasStatusSubresource[C] {}
  }

  /**
   * Returns a value that can be passed as the required implicit parameter to the 'getScale' and 'updateScale' method for the
   * given CR type
   * Requires the scale subresource to be defined on the custom resource definition for the type
   *
   * @param rd The resource definition for the type - the status subresource must be defined on it
   * @tparam C The specific CustomResource type for which the status methods should be enabled
   * @return HasStatusResource value that can be passed implicitly to the `updateStatus` method for this type
   */
  def scalingMethodsEnabler[C <: CustomResource[_, _]](implicit rd: ResourceDefinition[C]): Scale.SubresourceSpec[C] = {
    if (!rd.spec.subresources.map(_.scale).isDefined)
      throw new K8SException(Status(message = Some("Scale subresource must be defined on the associated resource definition before scaling methods can be enabled")))
    new Scale.SubresourceSpec[C] {
      override def apiVersion: String = "autoscaling/v1"
    }
  }

  /*
   * Generic formatter for custom resource types - this should be appropriate for most use cases, but can be
   * overridden by an application-specified formatter for specific custom resource types if necessary.
   * Note: the application needs to provide implicit formatters for the Spec and Status subresources
   */
  implicit def crFormat[Sp, St](implicit spFmt: Format[Sp], stFmt: Format[St]): Format[CustomResource[Sp, St]] = (objFormat and
    (JsPath \ "spec").format[Sp] and
    (JsPath \ "status").formatNullable[St]) (
    (kind, apiVersion, meta, sp, st) =>
      CustomResource[Sp, St](kind, apiVersion, meta, sp, st),
    res => (res.kind, res.apiVersion, res.metadata, res.spec, res.status))

  type CustomResourceList[Sp, St] = ListResource[CustomResource[Sp, St]]

  implicit def crListFormat[Sp, St](implicit spFmt: Format[Sp], stFmt: Format[St]):
  Format[CustomResourceList[Sp, St]] = ListResourceFormat[CustomResource[Sp, St]](crFormat(spFmt, stFmt))


}