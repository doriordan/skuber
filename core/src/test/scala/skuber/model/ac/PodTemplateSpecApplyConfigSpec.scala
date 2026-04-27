package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class PodTemplateSpecApplyConfigSpec extends AnyFlatSpec with Matchers {

  "PodTemplateSpecApplyConfig" should "serialize only set fields" in {
    val tmpl = PodTemplateSpecApplyConfig()
      .addLabel("app" -> "web")
      .addContainer(ContainerApplyConfig("nginx", "nginx:1.25"))
    val json = Json.toJson(tmpl)
    (json \ "metadata" \ "labels" \ "app").as[String] shouldBe "web"
    (json \ "spec" \ "containers")(0).as[play.api.libs.json.JsValue].\("name").as[String] shouldBe "nginx"
  }

  it should "serialize as empty object when nothing is set" in {
    val tmpl = PodTemplateSpecApplyConfig()
    val json = Json.toJson(tmpl)
    (json \ "metadata").toOption shouldBe None
    (json \ "spec").toOption shouldBe None
  }

  it should "delegate fluent methods to nested types" in {
    val tmpl = PodTemplateSpecApplyConfig()
      .addAnnotation("note" -> "test")
      .addVolume(Volume("data", Volume.EmptyDir()))
      .withServiceAccountName("my-sa")
      .withRestartPolicy(RestartPolicy.OnFailure)
    tmpl.metadata.get.annotations shouldBe Some(Map("note" -> "test"))
    tmpl.spec.get.volumes.get.head.name shouldBe "data"
    tmpl.spec.get.serviceAccountName shouldBe Some("my-sa")
    tmpl.spec.get.restartPolicy shouldBe Some(RestartPolicy.OnFailure)
  }
}
