package skuber.json.apps

import skuber._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.apps._
import skuber.json.format._ // reuse some core formatters

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
  )(Deployment.apply _, unlift(Deployment.unapply))

  // Stateful set formatters
  implicit val statefulSetPodPcyMgmtFmt: Format[StatefulSet.PodManagementPolicyType.PodManagementPolicyType] = Format(enumReads(StatefulSet.PodManagementPolicyType, StatefulSet.PodManagementPolicyType.OrderedReady), enumWrites)
  implicit val statefulSetRollUp: Format[StatefulSet.RollingUpdateStrategy] = Json.format[StatefulSet.RollingUpdateStrategy]
  implicit val statefulSetUpdStrFmt: Format[StatefulSet.UpdateStrategy] = (
    (JsPath \ "type").formatEnum(StatefulSet.UpdateStrategyType, Some(StatefulSet.UpdateStrategyType.RollingUpdate)) and
    (JsPath \ "rollingUpdate").formatNullable[StatefulSet.RollingUpdateStrategy]
  )(StatefulSet.UpdateStrategy.apply _,unlift(StatefulSet.UpdateStrategy.unapply))


  implicit val statefulSetSpecFmt: Format[StatefulSet.Spec] = (
    (JsPath \ "replicas").formatNullable[Int] and
    (JsPath \ "serviceName").formatNullable[String] and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "template").format[Pod.Template.Spec] and
    (JsPath \ "volumeClaimTemplates").formatMaybeEmptyList[PersistentVolumeClaim] and
    (JsPath \ "podManagmentPolicy").formatNullableEnum(StatefulSet.PodManagementPolicyType) and
    (JsPath \ "updateStrategy").formatNullable[StatefulSet.UpdateStrategy] and
    (JsPath \ "revisionHistoryLimit").formatNullable[Int]
  )(StatefulSet.Spec.apply _, unlift(StatefulSet.Spec.unapply))

  implicit val statefulSetCondFmt: Format[StatefulSet.Condition] = Json.format[StatefulSet.Condition]
  implicit val statefulSetStatusFmt: Format[StatefulSet.Status] = Json.format[StatefulSet.Status]

  implicit lazy val statefulSetFormat: Format[StatefulSet] = (
    objFormat and
      (JsPath \ "spec").formatNullable[StatefulSet.Spec] and
      (JsPath \ "status").formatNullable[StatefulSet.Status]
    ) (StatefulSet.apply _, unlift(StatefulSet.unapply))

  implicit val statefulSetListFormat: Format[StatefulSetList] = ListResourceFormat[StatefulSet]
  implicit val deployListFormat: Format[DeploymentList] = ListResourceFormat[Deployment]
}
