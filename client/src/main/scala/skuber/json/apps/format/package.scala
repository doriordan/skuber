package skuber.json.apps

import skuber.model.apps._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.json.format._
import skuber.model.Pod

/**
  * @author Hollin Wilkins
  *
  * Implicit JSON formatters for the apps API objects
  */
package object format {

  // Deployment formatters
  implicit val depStatusFmt: Format[Deployment.Status] = (
      (JsPath \ "replicas").formatMaybeEmptyInt() and
          (JsPath \ "updatedReplicas").formatMaybeEmptyInt() and
          (JsPath \ "availableReplicas").formatMaybeEmptyInt() and
          (JsPath \ "unavailableReplicas").formatMaybeEmptyInt() and
          (JsPath \ "observedGeneration").formatMaybeEmptyInt()
      )(Deployment.Status.apply, unlift(Deployment.Status.unapply))

  implicit val rollingUpdFmt: Format[Deployment.RollingUpdate] = (
      (JsPath \ "maxUnavailable").formatMaybeEmptyIntOrString(Left(1)) and
          (JsPath \ "maxSurge").formatMaybeEmptyIntOrString(Left(1))
      )(Deployment.RollingUpdate.apply, unlift(Deployment.RollingUpdate.unapply))

  implicit val depStrategyFmt: Format[Deployment.Strategy] =  (
    (JsPath \ "type").formatEnum(Deployment.StrategyType, Some(Deployment.StrategyType.RollingUpdate)) and
    (JsPath \ "rollingUpdate").formatNullable[Deployment.RollingUpdate]
  )(Deployment.Strategy.apply, unlift(Deployment.Strategy.unapply))

  implicit val depSpecFmt: Format[Deployment.Spec] = (
    (JsPath \ "replicas").formatNullable[Int] and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "template").formatNullable[Pod.Template.Spec] and
    (JsPath \ "strategy").formatNullable[Deployment.Strategy] and
    (JsPath \ "minReadySeconds").formatMaybeEmptyInt()
  )(Deployment.Spec.apply, unlift(Deployment.Spec.unapply))

  implicit lazy val depFormat: Format[Deployment] = (
    objFormat and
    (JsPath \ "spec").formatNullable[Deployment.Spec] and
    (JsPath \ "status").formatNullable[Deployment.Status]
  )(Deployment.apply, unlift(Deployment.unapply))

  implicit val deployListFormat: Format[DeploymentList] = ListResourceFormat[Deployment]
}
