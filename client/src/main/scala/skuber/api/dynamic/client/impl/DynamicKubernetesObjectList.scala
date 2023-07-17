
package skuber.api.dynamic.client.impl

import play.api.libs.json._
import skuber.ObjectMeta
import skuber.json.format._

// Dynamic kubernetes object list with a raw json response from kubernetes api.
case class DynamicKubernetesObjectList(jsonRaw: JsonRaw,
                                       resources: List[DynamicKubernetesObject])

object DynamicKubernetesObjectList {

  implicit val dynamicKubernetesObjectListFmt: Format[DynamicKubernetesObjectList] = new Format[DynamicKubernetesObjectList] {
    override def writes(o: DynamicKubernetesObjectList): JsValue = Json.writes[DynamicKubernetesObjectList].writes(o)

    override def reads(json: JsValue): JsResult[DynamicKubernetesObjectList] = {
      (json \ "items").asOpt[List[DynamicKubernetesObject]] match {
        case Some(items) => JsSuccess(DynamicKubernetesObjectList(JsonRaw(json), items))
        case None => JsError("items field is missing")
      }
    }

  }
}
