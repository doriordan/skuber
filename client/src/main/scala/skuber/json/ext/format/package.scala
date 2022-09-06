package skuber.json.ext

/**
 * @author David O'Riordan
 *
 *         Implicit JSON formatters for the extensions API objects
 */

import java.awt.font.ImageGraphicAttribute

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber._
import skuber.autoscaling.HorizontalPodAutoscaler
import skuber.ext._
import skuber.json.format._
//import skuber.networking.Ingress // reuse some core formatters

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

  // DaemonSet formatters

  implicit val daemonSetRollingUpdateFmt: Format[DaemonSet.RollingUpdate] = Json.format[DaemonSet.RollingUpdate]
  implicit val daemonSetUpdateStrategyFmt: Format[DaemonSet.UpdateStrategy] = Json.format[DaemonSet.UpdateStrategy]
  implicit val daemonsetStatusFmt: Format[DaemonSet.Status] = Json.format[DaemonSet.Status]
  implicit val daemonsetSpecFmt: Format[DaemonSet.Spec] = ((JsPath \ "minReadySeconds").formatMaybeEmptyInt() and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "template").formatNullable[Pod.Template.Spec] and
    (JsPath \ "updateStrategy").formatNullable[DaemonSet.UpdateStrategy] and
    (JsPath \ "revisionHistoryLimit").formatNullable[Int]) (DaemonSet.Spec.apply, d => (d.minReadySeconds, d.selector, d.template, d.updateStrategy, d.revisionHistoryLimit))

  implicit lazy val daemonsetFmt: Format[DaemonSet] = (objFormat and
    (JsPath \ "spec").formatNullable[DaemonSet.Spec] and
    (JsPath \ "status").formatNullable[DaemonSet.Status]) (DaemonSet.apply, d => (d.kind, d.apiVersion, d.metadata, d.spec, d.status))

  implicit val replsetSpecFormat: Format[ReplicaSet.Spec] = ((JsPath \ "replicas").formatNullable[Int] and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "template").formatNullable[Pod.Template.Spec]) (ReplicaSet.Spec.apply, r => (r.replicas, r.selector, r.template))

  implicit val replsetStatusFormat: OFormat[ReplicaSet.Status] = Json.format[ReplicaSet.Status]

  implicit lazy val replsetFormat: Format[ReplicaSet] = (objFormat and
    (JsPath \ "spec").formatNullable[ReplicaSet.Spec] and
    (JsPath \ "status").formatNullable[ReplicaSet.Status]) (ReplicaSet.apply, r => (r.kind, r.apiVersion, r.metadata, r.spec, r.status))

  implicit val daesetListFmt: Format[DaemonSetList] = ListResourceFormat[DaemonSet]
  implicit val replsetListFmt: Format[ReplicaSetList] = ListResourceFormat[ReplicaSet]
  implicit val deployListFormat: Format[DeploymentList] = ListResourceFormat[Deployment]

}
