package skuber

import com.sun.org.glassfish.external.statistics.Statistic
import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.apiextensions.CustomResourceDefinition
import skuber.json.format.objFormat

import scala.reflect.runtime.universe._

/*
 * CustomResource provides a generic model that can be used for custom resource types that follow the standard Kubernetes
 * pattern of being composed of "spec" and "status" subresources.
 */
case class CustomResource[Sp,St](
  override val kind: String,
  override val apiVersion: String,
  override val metadata: ObjectMeta,
  spec: Sp,
  status: Option[St]) extends ObjectResource
{
  def withMetadata(metadata: ObjectMeta) = this.copy(metadata = metadata)
  def withName(name: String) = withMetadata(metadata.copy(name = name))
  def withGenerateName(generateName: String) = withMetadata((metadata.copy(generateName = generateName)))
  def withNamespace(namespace: String) = withMetadata(metadata.copy(namespace= namespace))
  def withLabels(labels: Tuple2[String,String]*) = withMetadata(metadata.copy(labels=Map(labels: _*)))
  def withAnnotations(annotations: Tuple2[String,String]*) = withMetadata(metadata.copy(annotations=Map(annotations: _*)))
  def withFinalizers(finalizers: String*) = withMetadata(metadata.copy(finalizers=Some(finalizers.toList)))
  def withStatus(status: St): CustomResource[Sp,St] = this.copy(status=Some(status))
}

object CustomResource {

  /*
   * Make a new CustomResource of a specific type. The kind and apiVersion will be copied from the associated resource
   * definition.
   */
  def apply[Sp, St](spec: Sp)(implicit rd: ResourceDefinition[CustomResource[Sp,St]]) = new CustomResource[Sp,St](
    kind = rd.spec.names.kind,
    apiVersion = s"${rd.spec.group.get}/${rd.spec.defaultVersion}",
    metadata = ObjectMeta(),
    spec = spec,
    status = None)

  /**
    * This implicit enables the 'status' methods on the Skuber APi to be called on all CustomResource types
    * (note: those methods are applied to the status subresource of the custom resource, so for the calls to succeed the status
    * subresource must be enabled on Kubernetes by setting it on the associated custom resource definition)
    */
  implicit def enableStatus[Sp,St]: HasStatusSubresource[CustomResource[Sp,St]] = new HasStatusSubresource[CustomResource[Sp,St]] {}

  /*
   * To indicate that 'scale' subresource is supported for specific custom resource type, the application declares:
   * implicit val scaling=CustomResource.enableScaling[T] where T is the speicifc CustomResource type
   * If no such implicit is provided the compiler won't allow the scale methods on the API to be invoked
   */
  def enableScaling[O <: ObjectResource] =
    new Scale.SubresourceSpec[O] { override def apiVersion = "autoscaling/v1" }

  /*
   * Generic formatter for custom resource types - this should be appropriate for most use cases, but can be
   * overridden by an application-specified formatter for specific custom resource types if necessary.
   * Note: the application needs to provide implicit formatters for the Spec and Status subresources
   */
  implicit def crFormat[Sp,St](implicit spFmt: Format[Sp], stFmt: Format[St]): Format[CustomResource[Sp,St]] = (
    objFormat and
    (JsPath \ "spec").format[Sp] and
    (JsPath \ "status").formatNullable[St]
  )(CustomResource.apply _, unlift(CustomResource.unapply[Sp,St]))

  /*
   * Generic formatter required for parsing lists of custom resources - requires an implicit formatter for the corresponding
   * resource type to be in scope (which is usually just the above method)
   */
  implicit def crListFormat[CustomResource[Sp,St] <: ObjectResource, Sp, St](implicit ofmt: Format[CustomResource[Sp,St]]): Format[ListResource[CustomResource[Sp, St]]] = {
    import skuber.json.format.ListResourceFormat
    ListResourceFormat[CustomResource[Sp,St]]
  }

}