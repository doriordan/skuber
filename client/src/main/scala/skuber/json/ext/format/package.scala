package skuber.json.ext

/**
 * @author David O'Riordan
 *
 * Implicit JSON formatters for the extensions API objects
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
  implicit val depStatusFmt: Format[Deployment.Status] = (
    (JsPath \ "replicas").formatMaybeEmptyInt() and
      (JsPath \ "updatedReplicas").formatMaybeEmptyInt() and
      (JsPath \ "availableReplicas").formatMaybeEmptyInt() and
      (JsPath \ "unavailableReplicas").formatMaybeEmptyInt() and
      (JsPath \ "observedGeneration").formatMaybeEmptyInt()
    )(Deployment.Status.apply _, unlift(Deployment.Status.unapply))


  implicit val rollingUpdFmt: Format[Deployment.RollingUpdate] = (
    (JsPath \ "maxUnavailable").formatMaybeEmptyIntOrString(Left(1)) and
      (JsPath \ "maxSurge").formatMaybeEmptyIntOrString(Left(1))
    )(Deployment.RollingUpdate.apply _, unlift(Deployment.RollingUpdate.unapply))

  implicit val depStrategyFmt: Format[Deployment.Strategy] =  (
    (JsPath \ "type").formatEnum(Deployment.StrategyType, Some(Deployment.StrategyType.RollingUpdate)) and
      (JsPath \ "rollingUpdate").formatNullable[Deployment.RollingUpdate]
    )(Deployment.Strategy.apply _, unlift(Deployment.Strategy.unapply))

  implicit val depSpecFmt: Format[Deployment.Spec] = (
    (JsPath \ "replicas").formatNullable[Int] and
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

  // DaemonSet formatters

  implicit val daemonSetRollingUpdateFmt: Format[DaemonSet.RollingUpdate] = Json.format[DaemonSet.RollingUpdate]
  implicit val daemonSetUpdateStrategyFmt: Format[DaemonSet.UpdateStrategy] = Json.format[DaemonSet.UpdateStrategy]
  implicit val daemonsetStatusFmt: Format[DaemonSet.Status] = Json.format[DaemonSet.Status]
  implicit val daemonsetSpecFmt: Format[DaemonSet.Spec] = (
    (JsPath \ "minReadySeconds").formatMaybeEmptyInt() and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "template").formatNullable[Pod.Template.Spec] and
    (JsPath \ "updateStrategy").formatNullable[DaemonSet.UpdateStrategy] and
    (JsPath \ "revisionHistoryLimit").formatNullable[Int]
  )(DaemonSet.Spec.apply, unlift(DaemonSet.Spec.unapply))

  implicit lazy val daemonsetFmt: Format[DaemonSet] = (
    objFormat and
    (JsPath \ "spec").formatNullable[DaemonSet.Spec] and
    (JsPath \ "status").formatNullable[DaemonSet.Status]
  ) (DaemonSet.apply _, unlift(DaemonSet.unapply))

  implicit val replsetSpecFormat: Format[ReplicaSet.Spec] = (
    (JsPath \ "replicas").formatNullable[Int] and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "template").formatNullable[Pod.Template.Spec]
  )(ReplicaSet.Spec.apply _, unlift(ReplicaSet.Spec.unapply))

  implicit val replsetStatusFormat = Json.format[ReplicaSet.Status]

  implicit lazy val replsetFormat: Format[ReplicaSet] = (
    objFormat and
    (JsPath \ "spec").formatNullable[ReplicaSet.Spec] and
    (JsPath \ "status").formatNullable[ReplicaSet.Status]
  ) (ReplicaSet.apply _, unlift(ReplicaSet.unapply))

  implicit val daesetListFmt: Format[DaemonSetList] = ListResourceFormat[DaemonSet]
  implicit val replsetListFmt: Format[ReplicaSetList] = ListResourceFormat[ReplicaSet]
  implicit val deployListFormat: Format[DeploymentList] = ListResourceFormat[Deployment]

}
