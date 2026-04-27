package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._

class ConfigMapApplyConfigSpec extends AnyFlatSpec with Matchers {

  "ConfigMapApplyConfig" should "be constructed by name" in {
    val cm = ConfigMapApplyConfig("my-config")
    cm.name shouldBe "my-config"
    cm.kind shouldBe "ConfigMap"
    cm.apiVersion shouldBe "v1"
  }

  it should "serialize with kind, apiVersion, and only set fields" in {
    val cm = ConfigMapApplyConfig("my-config")
      .withData(Map("key1" -> "value1"))
    val json = Json.toJson(cm)
    (json \ "kind").as[String] shouldBe "ConfigMap"
    (json \ "apiVersion").as[String] shouldBe "v1"
    (json \ "metadata" \ "name").as[String] shouldBe "my-config"
    (json \ "data" \ "key1").as[String] shouldBe "value1"
    (json \ "binaryData").toOption shouldBe None
  }

  it should "omit data when not set" in {
    val cm = ConfigMapApplyConfig("my-config")
    val json = Json.toJson(cm)
    (json \ "data").toOption shouldBe None
  }

  it should "support addData" in {
    val cm = ConfigMapApplyConfig("my-config")
      .addData("k1" -> "v1")
      .addData("k2" -> "v2")
    cm.data shouldBe Some(Map("k1" -> "v1", "k2" -> "v2"))
  }

  it should "extend ApplyConfiguration[ConfigMap]" in {
    val cm: ApplyConfiguration[ConfigMap] = ConfigMapApplyConfig("my-config")
    cm.name shouldBe "my-config"
  }
}
