package skuber.json.ext

/**
 * @author David O'Riordan
 * 
 * Implicit JSON formatters for the extensions API objects
 */
import skuber._
import skuber.ext._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import skuber.json.format._ // reuse some core formatters

package object format {
  // Scale formatters
  implicit val scaleStatusFormat: Format[Scale.Status] = (
      (JsPath \ "replicas").formatMaybeEmptyInt() and
          (JsPath \ "selector").formatNullableLabelSelector and
          (JsPath \ "targetSelector").formatNullableLabelSelector
      )(Scale.Status.apply _, unlift(Scale.Status.unapply))
  implicit val scaleSpecFormat: Format[Scale.Spec] = Json.format[Scale.Spec]
  implicit val scaleFormat: Format[Scale] = Json.format[Scale]
  
  // SubresourceReference formatter
  implicit val subresFmt: Format[SubresourceReference] = (
    (JsPath \ "kind").formatMaybeEmptyString() and
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "apiVersion").formatMaybeEmptyString() and
    (JsPath \ "subresource").formatMaybeEmptyString()
  )(SubresourceReference.apply _, unlift(SubresourceReference.unapply))
 
  // HorizontalPodAutoscaler formatters
  implicit val cpuTUFmt: Format[CPUTargetUtilization] = Json.format[CPUTargetUtilization]
  implicit val hpasSpecFmt: Format[HorizontalPodAutoscaler.Spec] = Json.format[HorizontalPodAutoscaler.Spec]
  implicit val hpasStatusFmt: Format[HorizontalPodAutoscaler.Status] = Json.format[HorizontalPodAutoscaler.Status]
  implicit val hpasFmt: Format[HorizontalPodAutoscaler] =  Json.format[HorizontalPodAutoscaler]
   
  // Deployment formatters
  implicit val depStatusFmt: Format[Deployment.Status] = (
    (JsPath \ "replicas").formatMaybeEmptyInt() and
    (JsPath \ "updatedReplicas").formatMaybeEmptyInt() and
    (JsPath \ "availableReplicas").formatMaybeEmptyInt() and
    (JsPath \ "observedGeneration").formatMaybeEmptyInt()
  )(Deployment.Status.apply _, unlift(Deployment.Status.unapply))
  
    
  implicit val rollingUpdFmt: Format[Deployment.RollingUpdate] = (
    (JsPath \ "maxUnavailable").formatMaybeEmptyIntOrString(Left(1)) and
    (JsPath \ "maxSurge").formatMaybeEmptyIntOrString(Left(1))
  )(Deployment.RollingUpdate.apply _, unlift(Deployment.RollingUpdate.unapply))
  
  implicit val depStrategyFmt: Format[Deployment.Strategy] =  (
    (JsPath \ "type").formatEnum(Deployment.StrategyType) and
    (JsPath \ "rollingUpdate").formatNullable[Deployment.RollingUpdate]
  )(Deployment.Strategy.apply _, unlift(Deployment.Strategy.unapply))
  
  implicit val depSpecFmt: Format[Deployment.Spec] = (
    (JsPath \ "replicas").formatMaybeEmptyInt() and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "template").formatNullable[Pod.Template.Spec] and
    (JsPath \ "strategy").formatNullable[Deployment.Strategy] and
    (JsPath \ "minReadySeconds").formatMaybeEmptyInt()
  )(Deployment.Spec.apply _, unlift(Deployment.Spec.unapply))
 
  implicit lazy val depFormat: Format[Deployment] = (
    objFormat and
    (JsPath \ "spec").formatNullable[Deployment.Spec] and
    (JsPath \ "status").formatNullable[Deployment.Status]
  ) (Deployment.apply _, unlift(Deployment.unapply))
}