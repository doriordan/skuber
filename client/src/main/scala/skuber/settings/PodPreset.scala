package skuber.settings

import play.api.libs.functional.syntax._
import play.api.libs.json.{ Format, JsPath }
import skuber.json.format.{
  objFormat,
  maybeEmptyFormatMethods,
  jsPath2LabelSelFormat,
  envVarFormat,
  envFromSourceFmt,
  volMountFormat,
  volumeFormat
}
import skuber.ResourceSpecification.{ Names, Scope }
import skuber.{
  EnvFromSource,
  EnvVar,
  LabelSelector,
  NonCoreResourceSpecification,
  ObjectMeta,
  ObjectResource,
  ResourceDefinition,
  Volume
}

/**
  * @author David O'Riordan
  */
case class PodPreset(
    kind: String = "PodPreset",
    override val apiVersion: String = "settings.k8s.io/v1alpha1",
    metadata: ObjectMeta,
    spec: Option[PodPreset.Spec] = None
) extends ObjectResource

object PodPreset {

  case class Spec(
      selector: LabelSelector,
      env: List[EnvVar] = Nil,
      envFrom: List[EnvFromSource] = Nil,
      volumes: List[Volume] = Nil,
      volumeMounts: List[Volume.Mount] = Nil
  )

  // Kubernetes resource specification

  val specification = NonCoreResourceSpecification(
    apiGroup = "settings.k8s.io",
    version = "v1alpha1",
    scope = Scope.Namespaced,
    names = Names(
      plural = "podpresets",
      singular = "podpreset",
      kind = "PodPreset",
      shortNames = List()
    )
  )

  implicit val ppDef: ResourceDefinition[PodPreset] = new ResourceDefinition[PodPreset] {
    def spec: NonCoreResourceSpecification = specification
  }
  implicit val pplListDef: ResourceDefinition[PodPresetList] = new ResourceDefinition[PodPresetList] {
    def spec: NonCoreResourceSpecification = specification
  }

  // Json formatters
  implicit val podPresetSpecFmt: Format[Spec] = (
    (JsPath \ "selector").formatLabelSelector and
      (JsPath \ "env").formatMaybeEmptyList[EnvVar] and
      (JsPath \ "envFrom").formatMaybeEmptyList[EnvFromSource] and
      (JsPath \ "volumes").formatMaybeEmptyList[Volume] and
      (JsPath \ "volumeMounts").formatMaybeEmptyList[Volume.Mount]
  )(Spec.apply, unlift(Spec.unapply))

  implicit val podPresetFmt: Format[PodPreset] = (
    objFormat and
      (JsPath \ "spec").formatNullable[Spec]
  )(PodPreset.apply, unlift(PodPreset.unapply))

}
