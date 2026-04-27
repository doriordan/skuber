package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class ServiceAccountApplyConfigSpec extends AnyFlatSpec with Matchers {

  "ServiceAccountApplyConfig" should "be constructed by name" in {
    val sa = ServiceAccountApplyConfig("my-sa")
    sa.name shouldBe "my-sa"
    sa.kind shouldBe "ServiceAccount"
    sa.apiVersion shouldBe "v1"
  }

  it should "serialize with kind, apiVersion, and only set fields" in {
    val sa = ServiceAccountApplyConfig("my-sa")
      .addImagePullSecret("my-registry-key")
    val json = Json.toJson(sa)
    (json \ "kind").as[String] shouldBe "ServiceAccount"
    (json \ "apiVersion").as[String] shouldBe "v1"
    (json \ "metadata" \ "name").as[String] shouldBe "my-sa"
    (json \ "imagePullSecrets")(0).as[LocalObjectReference].name shouldBe "my-registry-key"
  }

  it should "omit secrets and imagePullSecrets when not set" in {
    val sa = ServiceAccountApplyConfig("my-sa")
    val json = Json.toJson(sa)
    (json \ "secrets").toOption shouldBe None
    (json \ "imagePullSecrets").toOption shouldBe None
  }

  it should "extend ApplyConfiguration[ServiceAccount]" in {
    val sa: ApplyConfiguration[ServiceAccount] = ServiceAccountApplyConfig("my-sa")
    sa.name shouldBe "my-sa"
  }
}
