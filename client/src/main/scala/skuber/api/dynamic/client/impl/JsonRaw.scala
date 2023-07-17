package skuber.api.dynamic.client.impl

import play.api.libs.json.{JsValue, Json, OFormat}

case class JsonRaw(jsValue: JsValue) {
  override def toString: String = Json.stringify(jsValue)
}
object JsonRaw {
  implicit val jsonRawFmt: OFormat[JsonRaw] = Json.format[JsonRaw]
}
