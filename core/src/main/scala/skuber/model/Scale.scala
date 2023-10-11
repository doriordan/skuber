package skuber.model

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json}

import skuber.json.format._

/**
 * @author David O'Riordan
 *  Scale subresource
 */
case class Scale(
  kind: String = "Scale",
  apiVersion: String,
  metadata: ObjectMeta,
  spec: Scale.Spec = Scale.Spec(),
  status: Option[Scale.Status] = None)
    extends ObjectResource
{
  def withSpecReplicas(count: Int) =  this.copy(spec=Scale.Spec(Some(count)))
  def withStatusReplicas(count: Int) = {
    val newStatus = this.status.map(_.copy(replicas = count)).getOrElse(Scale.Status(replicas=count))
    this.copy(status=Some(newStatus))
  }
}
    
object Scale {

  def named(name: String, apiVersion: String=v1) = new Scale(apiVersion=apiVersion,metadata=ObjectMeta(name=name))

  case class Spec(replicas: Option[Int] = None)
  object Spec {
    implicit val scaleSpecFormat: Format[Scale.Spec] = Json.format[Scale.Spec]
  }

  case class Status(
    replicas: Int = 0,
    selector: Option[LabelSelector] = None,
    targetSelector: Option[String] = None
  )

  object Status {
    implicit val scaleStatusFormat: Format[Scale.Status] = (
      (JsPath \ "replicas").formatMaybeEmptyInt() and
      (JsPath \ "selector").formatNullableLabelSelector and
      (JsPath \ "targetSelector").formatNullable[String]
    )(Scale.Status.apply _, s => (s.replicas, s.selector, s.targetSelector))
  }

  implicit val scaleFormat: Format[Scale] = Json.format[Scale]

  // Any object resource type [O <: ObjectResource] that supports a Scale subresource must provide an implicit value of
  // SubresourceSpec type to enable the client API method `scale` to be used on such resources
  // Kubernetes supports Scale subresources on ReplicationController/ReplicaSet/Deployment/StatefulSet types
  trait SubresourceSpec[O <: ObjectResource] {
    def apiVersion: String // the API version to be set on any Scale subresource of the specific resource type O
  }
}    