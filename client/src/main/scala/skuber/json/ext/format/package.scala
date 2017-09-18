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
import skuber.ext.Ingress.Backend
import skuber.ext._
import skuber.json.format._ // reuse some core formatters

package object format {

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

  implicit val ingressBackendFmt: Format[Ingress.Backend] = Json.format[Ingress.Backend]

  implicit val ingressPathFmt: Format[Ingress.Path] = (
    (JsPath \ "path").formatMaybeEmptyString() and
      (JsPath \ "backend").format[Backend]
  ) (Ingress.Path.apply _, unlift(Ingress.Path.unapply))


  implicit val ingressHttpRuledFmt: Format[Ingress.HttpRule] = Json.format[Ingress.HttpRule]
  implicit val ingressRuleFmt: Format[Ingress.Rule] = Json.format[Ingress.Rule]
  implicit val ingressTLSFmt: Format[Ingress.TLS] = Json.format[Ingress.TLS]

  implicit val ingressSpecFormat: Format[Ingress.Spec] = (
      (JsPath \ "backend").formatNullable[Ingress.Backend] and
          (JsPath \ "rules").formatMaybeEmptyList[Ingress.Rule] and
          (JsPath \ "tls").formatMaybeEmptyList[Ingress.TLS]
      )(Ingress.Spec.apply _, unlift(Ingress.Spec.unapply))


  implicit val ingrlbingFormat: Format[Ingress.Status.LoadBalancer.Ingress] =
    Json.format[Ingress.Status.LoadBalancer.Ingress]

  implicit val ingrlbFormat: Format[Ingress.Status.LoadBalancer] = (
      (JsPath \ "ingress").formatMaybeEmptyList[Ingress.Status.LoadBalancer.Ingress].inmap(
        ings => Ingress.Status.LoadBalancer(ings),
        lb => lb.ingress
      )
  )

  implicit val ingressStatusFormat = Json.format[Ingress.Status]

  implicit lazy val ingressFormat: Format[Ingress] = (
      objFormat and
          (JsPath \ "spec").formatNullable[Ingress.Spec] and
          (JsPath \ "status").formatNullable[Ingress.Status]
      ) (Ingress.apply _, unlift(Ingress.unapply))

  implicit val daesetListFmt: Format[DaemonSetList] = ListResourceFormat[DaemonSet]
  implicit val replsetListFmt: Format[ReplicaSetList] = ListResourceFormat[ReplicaSet]
  implicit val ingressListFmt: Format[IngressList] = ListResourceFormat[Ingress]
}
