package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class SecretApplyConfigSpec extends AnyFlatSpec with Matchers {

  "SecretApplyConfig" should "be constructed by name" in {
    val secret = SecretApplyConfig("my-secret")
    secret.name shouldBe "my-secret"
    secret.kind shouldBe "Secret"
    secret.apiVersion shouldBe "v1"
  }

  it should "serialize with kind, apiVersion, and only set fields" in {
    val secret = SecretApplyConfig("my-secret")
      .withData(Map("password" -> "cGFzc3dvcmQ=".getBytes))
      .withType("Opaque")
    val json = Json.toJson(secret)
    (json \ "kind").as[String] shouldBe "Secret"
    (json \ "apiVersion").as[String] shouldBe "v1"
    (json \ "metadata" \ "name").as[String] shouldBe "my-secret"
    (json \ "type").as[String] shouldBe "Opaque"
  }

  it should "omit data and type when not set" in {
    val secret = SecretApplyConfig("my-secret")
    val json = Json.toJson(secret)
    (json \ "data").toOption shouldBe None
    (json \ "type").toOption shouldBe None
  }

  it should "extend ApplyConfiguration[Secret]" in {
    val secret: ApplyConfiguration[Secret] = SecretApplyConfig("my-secret")
    secret.name shouldBe "my-secret"
  }

  it should "support addData" in {
    val secret = SecretApplyConfig("my-secret")
      .addData("key1" -> "val1".getBytes)
      .addData("key2" -> "val2".getBytes)
    secret.data.get.size shouldBe 2
  }

  it should "support withStringData" in {
    val secret = SecretApplyConfig("my-secret")
      .withStringData(Map("config.yaml" -> "key: value"))
    secret.stringData shouldBe Some(Map("config.yaml" -> "key: value"))
  }
}
