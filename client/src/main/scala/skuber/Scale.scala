package skuber

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json}
import skuber.apps.{Deployment, StatefulSet}
import skuber.json.format.{maybeEmptyFormatMethods,objectMetaFormat}

/**
 * @author David O'Riordan
 */
case class Scale(
    val kind: String = "Scale",
    val apiVersion: String,
    val metadata: ObjectMeta,
    spec: Scale.Spec = Scale.Spec(),
    status: Option[Scale.Status] = None) extends ObjectResource {
  
  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

  def withReplicas(count: Int) = this.copy(spec=Scale.Spec(count))
}
    
object Scale {
  def named(name: String, apiVersion: String=v1) = new Scale(apiVersion=apiVersion,metadata=ObjectMeta(name=name))

  case class Spec(replicas: Int = 0)
  object Spec {
    implicit val scaleSpecFormat: Format[Scale.Spec] = Json.format[Scale.Spec]
  }
  case class Status(
    replicas: Int = 0,
    selector: Option[String] = None,
    targetSelector: Option[String] = None
  )

  object Status {
    implicit val scaleStatusFormat: Format[Scale.Status] = (
        (JsPath \ "replicas").formatMaybeEmptyInt() and
        (JsPath \ "selector").formatNullable[String] and
        (JsPath \ "targetSelector").formatNullable[String]
        )(Scale.Status.apply _, unlift(Scale.Status.unapply))
  }

  implicit val scaleSpecFormat: Format[Scale.Spec] = Json.format[Scale.Spec]
  implicit val scaleFormat: Format[Scale] = Json.format[Scale]


}    