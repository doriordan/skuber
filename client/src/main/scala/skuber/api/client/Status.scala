package skuber.api.client

import play.api.libs.json.{JsValue, Json, OFormat}
import skuber.ListMeta
import skuber.json.format.listMetaFormat
/**
  * @author David O'Riordan
  * Represents the status information typically returned in error responses from the Kubernetes API
  */
case class Status(apiVersion: String = "v1",
  kind: String = "Status",
  metadata: ListMeta = ListMeta(),
  status: Option[String] = None,
  message: Option[String]= None,
  reason: Option[String] = None,
  details: Option[JsValue] = None,
  code: Option[Int] = None  // HTTP status code
) {
  override def toString: String = Json.prettyPrint(Json.toJson(this))

}

object Status {

  implicit val statusFmt: OFormat[Status] = Json.format[Status]
}
