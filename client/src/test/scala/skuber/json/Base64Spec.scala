package skuber.json

import org.specs2.mutable.Specification
import play.api.libs.json.{ JsString, Json }
import skuber.json.format._

import java.util.Base64

class Base64Spec extends Specification {

  "Base64 format should round trip to/from string" >> {
    val dataBytes = "hello".getBytes
    val jsString = Json.toJson(dataBytes)
    jsString mustEqual JsString(Base64.getEncoder.encodeToString(dataBytes))
    new String(jsString.as[Array[Byte]], "utf-8") mustEqual "hello"
  }

}
