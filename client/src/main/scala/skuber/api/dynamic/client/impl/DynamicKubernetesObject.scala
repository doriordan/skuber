
package skuber.api.dynamic.client.impl

import play.api.libs.json._
import skuber.ObjectMeta
import skuber.json.format._

// Dynamic kubernetes object with a raw json response from kubernetes api.
case class DynamicKubernetesObject(jsonRaw: JsonRaw,
                                   apiVersion: Option[String],
                                   kind: Option[String],
                                   metadata: Option[ObjectMeta])

object DynamicKubernetesObject {

  implicit val dynamicKubernetesObjectFmt: Format[DynamicKubernetesObject] = new Format[DynamicKubernetesObject] {
    override def writes(o: DynamicKubernetesObject): JsValue = Json.writes[DynamicKubernetesObject].writes(o)

    override def reads(json: JsValue): JsResult[DynamicKubernetesObject] = {
      val apiVersion = (json \ "apiVersion").asOpt[String]
      val kind = (json \ "kind").asOpt[String]
      val metadata = (json \ "metadata").asOpt[ObjectMeta]
      JsSuccess(DynamicKubernetesObject(JsonRaw(json), apiVersion, kind, metadata))
    }

  }
}
