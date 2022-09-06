package skuber

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json}
import skuber.json.format.{maybeEmptyFormatMethods,jsPath2LabelSelFormat,objectMetaFormat}

/**
 * @author David O'Riordan
 *  Scale subresource
 */
case class Scale(val kind: String = "Scale",
    val apiVersion: String,
    val metadata: ObjectMeta,
    spec: Scale.Spec = Scale.Spec(),
    status: Option[Scale.Status] = None) extends ObjectResource
{
  def withSpecReplicas(count: Int) =  this.copy(spec=Scale.Spec(Some(count)))
  def withStatusReplicas(count: Int) = {
    val newStatus = this.status.map(_.copy(replicas = count)).getOrElse(Scale.Status(replicas=count))
    this.copy(status=Some(newStatus))
  }

  val statusSelectorLabels: Map[String, String] =
    status.flatMap(_.selector.map { labels =>
      val labelsArr = labels.split(",")
      labelsArr.map { singleLabel =>
        singleLabel.split("=") match {
          case Array(key, value) => key -> value
          case _=> singleLabel -> singleLabel
        }
      }.toMap
    }).getOrElse(Map.empty)

}
    
object Scale {

  def named(name: String, apiVersion: String=v1) = new Scale(apiVersion=apiVersion,metadata=ObjectMeta(name=name))

  case class Spec(replicas: Option[Int] = None)
  object Spec {
    implicit val scaleSpecFormat: Format[Scale.Spec] = Json.format[Scale.Spec]
  }

  case class Status(replicas: Int = 0,
    selector: Option[String] = None,
    targetSelector: Option[String] = None)

  object Status {
    implicit val scaleStatusFormat: Format[Scale.Status] = ((JsPath \ "replicas").formatMaybeEmptyInt() and
      (JsPath \ "selector").formatNullable[String] and
      (JsPath \ "targetSelector").formatNullable[String])((replicas, selector, targetSelector) => Scale.Status(replicas, selector, targetSelector),
      scale => (scale.replicas, scale.selector, scale.targetSelector))
  }

  implicit val scaleFormat: Format[Scale] = Json.format[Scale]

  // Any object resource type [O <: ObjectResource] that supports a Scale subresource must provide an implicit value of
  // SubresourceSpec type to enable the client API method `scale` to be used on such resources
  // Kubernetes supports Scale subresources on ReplicationController/ReplicaSet/Deployment/StatefulSet types
  trait SubresourceSpec[O <: ObjectResource] {
    def apiVersion: String // the API version to be set on any Scale subresource of the specific resource type O
  }
}    