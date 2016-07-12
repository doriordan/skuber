package skuber.json.ext

/**
 * @author David O'Riordan
 * 
 * Implicit JSON formatters for the extensions API objects
 */

import java.awt.font.ImageGraphicAttribute

import skuber._
import skuber.ext.Ingress.Backend
import skuber.ext._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import skuber.json.format._ // reuse some core formatters

package object format {
  // Scale formatters
  implicit val scaleStatusFormat: Format[Scale.Status] = (
    (JsPath \ "replicas").formatMaybeEmptyInt() and
    (JsPath \ "selector").formatMaybeEmptyString() and
    (JsPath \ "targetSelector").formatNullable[String]
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

  // DaemonSet formatters
  implicit val daemonsetStatusFmt: Format[DaemonSet.Status] = Json.format[DaemonSet.Status]
  implicit val daemonsetSpecFormat: Format[DaemonSet.Spec] = (
    (JsPath \ "selector").formatNullableLabelSelector and
      (JsPath \ "template").formatNullable[Pod.Template.Spec]
    )(DaemonSet.Spec.apply, unlift(DaemonSet.Spec.unapply))
  implicit val daemonsetFmt: Format[DaemonSet] = Json.format[DaemonSet]

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

  implicit val replsetSpecFormat: Format[ReplicaSet.Spec] = (
      (JsPath \ "replicas").formatMaybeEmptyInt() and
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
  implicit val ingressPathFmt: Format[Ingress.Path] = Json.format[Ingress.Path]
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

  implicit val ingrlbFormat: Format[Ingress.Status.LoadBalancer] =
    Json.format[Ingress.Status.LoadBalancer]

  implicit val ingressStatusFormat = Json.format[Ingress.Status]

  implicit lazy val ingressFormat: Format[Ingress] = (
      objFormat and
          (JsPath \ "spec").formatNullable[Ingress.Spec] and
          (JsPath \ "status").formatNullable[Ingress.Status]
      ) (Ingress.apply _, unlift(Ingress.unapply))

  implicit val deplListFmt: Format[DeploymentList] = KListFormat[Deployment].apply(DeploymentList.apply _,unlift(DeploymentList.unapply))
  implicit val hpasListFmt: Format[HorizontalPodAutoscalerList] = KListFormat[HorizontalPodAutoscaler].apply(HorizontalPodAutoscalerList.apply _,unlift(HorizontalPodAutoscalerList.unapply))
  implicit val replsetListFmt: Format[ReplicaSetList] = KListFormat[ReplicaSet].apply(ReplicaSetList.apply _,unlift(ReplicaSetList.unapply))
  implicit val ingressListFmt: Format[IngressList] = KListFormat[Ingress].apply(IngressList.apply _,unlift(IngressList.unapply))
}
