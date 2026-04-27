package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._

class NamespaceApplyConfigSpec extends AnyFlatSpec with Matchers {

  "NamespaceApplyConfig" should "be constructed by name" in {
    val ns = NamespaceApplyConfig("my-namespace")
    ns.name shouldBe "my-namespace"
    ns.kind shouldBe "Namespace"
    ns.apiVersion shouldBe "v1"
  }

  it should "serialize with kind, apiVersion, and metadata" in {
    val ns = NamespaceApplyConfig("my-namespace")
      .addLabel("env" -> "production")
    val json = Json.toJson(ns)
    (json \ "kind").as[String] shouldBe "Namespace"
    (json \ "apiVersion").as[String] shouldBe "v1"
    (json \ "metadata" \ "name").as[String] shouldBe "my-namespace"
    (json \ "metadata" \ "labels" \ "env").as[String] shouldBe "production"
  }

  it should "extend ApplyConfiguration[Namespace]" in {
    val ns: ApplyConfiguration[Namespace] = NamespaceApplyConfig("my-namespace")
    ns.name shouldBe "my-namespace"
  }
}
