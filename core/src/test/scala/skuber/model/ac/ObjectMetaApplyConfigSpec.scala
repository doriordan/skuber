package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

class ObjectMetaApplyConfigSpec extends AnyFlatSpec with Matchers {

  "ObjectMetaApplyConfig" should "serialize only set fields, omitting None values" in {
    val meta = ObjectMetaApplyConfig(name = Some("test-pod"))
    val json = Json.toJson(meta)(ObjectMetaApplyConfig.writes)
    (json \ "name").as[String] shouldBe "test-pod"
    (json \ "namespace").toOption shouldBe None
    (json \ "labels").toOption shouldBe None
    (json \ "annotations").toOption shouldBe None
    (json \ "finalizers").toOption shouldBe None
  }

  it should "serialize all set fields" in {
    val meta = ObjectMetaApplyConfig(
      name = Some("test"),
      namespace = Some("default"),
      labels = Some(Map("app" -> "web")),
      annotations = Some(Map("note" -> "test")),
      finalizers = Some(List("finalizer.example.com"))
    )
    val json = Json.toJson(meta)(ObjectMetaApplyConfig.writes)
    (json \ "name").as[String] shouldBe "test"
    (json \ "namespace").as[String] shouldBe "default"
    (json \ "labels" \ "app").as[String] shouldBe "web"
    (json \ "annotations" \ "note").as[String] shouldBe "test"
    (json \ "finalizers")(0).as[String] shouldBe "finalizer.example.com"
  }

  it should "serialize an empty ObjectMetaApplyConfig as an empty JSON object" in {
    val meta = ObjectMetaApplyConfig()
    val json = Json.toJson(meta)(ObjectMetaApplyConfig.writes)
    json shouldBe Json.obj()
  }

  "ObjectMetaApplyConfig fluent API" should "support addLabel" in {
    val meta = ObjectMetaApplyConfig().addLabel("app" -> "web").addLabel("env" -> "prod")
    meta.labels shouldBe Some(Map("app" -> "web", "env" -> "prod"))
  }

  it should "support addAnnotation" in {
    val meta = ObjectMetaApplyConfig().addAnnotation("note" -> "test")
    meta.annotations shouldBe Some(Map("note" -> "test"))
  }

  it should "support withName and withNamespace" in {
    val meta = ObjectMetaApplyConfig().withName("test").withNamespace("default")
    meta.name shouldBe Some("test")
    meta.namespace shouldBe Some("default")
  }
}
