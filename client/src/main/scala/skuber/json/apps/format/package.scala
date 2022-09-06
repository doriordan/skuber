package skuber.json.apps

import skuber._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.apps._
import skuber.json.format._ // reuse some core formatters

/**
 * @author Hollin Wilkins
 *
 *         Implicit JSON formatters for the apps API objects
 */
package object format {

  // Deployment formatters
  implicit val depStatusFmt: Format[Deployment.Status] = ((JsPath \ "replicas").formatMaybeEmptyInt() and
    (JsPath \ "updatedReplicas").formatMaybeEmptyInt() and
    (JsPath \ "availableReplicas").formatMaybeEmptyInt() and
    (JsPath \ "unavailableReplicas").formatMaybeEmptyInt() and
    (JsPath \ "observedGeneration").formatMaybeEmptyInt()) (Deployment.Status.apply, d => (d.replicas, d.updatedReplicas, d.availableReplicas, d.unavailableReplicas, d.observedGeneration))

  implicit val rollingUpdFmt: Format[Deployment.RollingUpdate] = ((JsPath \ "maxUnavailable").formatMaybeEmptyIntOrString(Left(1)) and
    (JsPath \ "maxSurge").formatMaybeEmptyIntOrString(Left(1))) (Deployment.RollingUpdate.apply, r => (r.maxUnavailable, r.maxSurge))

  implicit val depStrategyFmt: Format[Deployment.Strategy] = ((JsPath \ "type").formatEnum(Deployment.StrategyType, Deployment.StrategyType.RollingUpdate.toString) and
    (JsPath \ "rollingUpdate").formatNullable[Deployment.RollingUpdate]) (Deployment.Strategy.apply, unlift(Deployment.Strategy.unapply))

  implicit val depSpecFmt: Format[Deployment.Spec] = ((JsPath \ "replicas").formatNullable[Int] and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "template").formatNullable[Pod.Template.Spec] and
    (JsPath \ "strategy").formatNullable[Deployment.Strategy] and
    (JsPath \ "minReadySeconds").formatMaybeEmptyInt()) (Deployment.Spec.apply, d => (d.replicas, d.selector, d.template, d.strategy, d.minReadySeconds))

  implicit lazy val depFormat: Format[Deployment] = (objFormat and
    (JsPath \ "spec").formatNullable[Deployment.Spec] and
    (JsPath \ "status").formatNullable[Deployment.Status]) (Deployment.apply, d => (d.kind, d.apiVersion, d.metadata, d.spec, d.status))

  implicit val deployListFormat: Format[DeploymentList] = ListResourceFormat[Deployment]
}
