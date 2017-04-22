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
  // Stateful set formatters
  implicit val statefulSetStatusFmt: Format[StatefulSet.Status] = (
    (JsPath \ "replicas").formatMaybeEmptyInt() and
      (JsPath \ "observedGeneration").formatMaybeEmptyInt()
    )(StatefulSet.Status.apply _, unlift(StatefulSet.Status.unapply))

  implicit val statefulSetSpecFmt: Format[StatefulSet.Spec] = (
    (JsPath \ "replicas").formatNullable[Int] and
      (JsPath \ "serviceName").formatNullable[String] and
      (JsPath \ "selector").formatNullableLabelSelector and
      (JsPath \ "template").formatNullable[Pod.Template.Spec] and
      (JsPath \ "volumeClaimTemplates").format[List[PersistentVolumeClaim]]
    )(StatefulSet.Spec.apply _, unlift(StatefulSet.Spec.unapply))

  implicit lazy val statefulSetFormat: Format[StatefulSet] = (
    objFormat and
      (JsPath \ "spec").formatNullable[StatefulSet.Spec] and
      (JsPath \ "status").formatNullable[StatefulSet.Status]
    ) (StatefulSet.apply _, unlift(StatefulSet.unapply))

  implicit val statefulSetListFormat: Format[StatefulSetList] = KListFormat[StatefulSet].apply(StatefulSetList.apply _,unlift(StatefulSetList.unapply))
}
