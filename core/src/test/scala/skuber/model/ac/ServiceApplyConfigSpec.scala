package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class ServiceApplyConfigSpec extends AnyFlatSpec with Matchers {

  "ServiceApplyConfig" should "be constructed by name" in {
    val svc = ServiceApplyConfig("my-service")
    svc.name shouldBe "my-service"
    svc.kind shouldBe "Service"
    svc.apiVersion shouldBe "v1"
  }

  it should "serialize with kind, apiVersion, and only set fields" in {
    val svc = ServiceApplyConfig("my-service")
      .withSelector(Map("app" -> "web"))
      .setPort(Service.Port(port = 80))
    val json = Json.toJson(svc)
    (json \ "kind").as[String] shouldBe "Service"
    (json \ "apiVersion").as[String] shouldBe "v1"
    (json \ "metadata" \ "name").as[String] shouldBe "my-service"
    (json \ "spec" \ "selector" \ "app").as[String] shouldBe "web"
    (json \ "spec" \ "ports")(0).as[play.api.libs.json.JsValue].\("port").as[Int] shouldBe 80
  }

  it should "omit spec when not set" in {
    val svc = ServiceApplyConfig("my-service")
    val json = Json.toJson(svc)
    (json \ "spec").toOption shouldBe None
  }

  it should "extend ApplyConfiguration[Service]" in {
    val svc: ApplyConfiguration[Service] = ServiceApplyConfig("my-service")
    svc.name shouldBe "my-service"
  }

  "ServiceSpecApplyConfig" should "serialize only set fields" in {
    val spec = ServiceSpecApplyConfig().withSelector(Map("app" -> "web"))
    val json = Json.toJson(spec)(ServiceSpecApplyConfig.writes)
    (json \ "selector" \ "app").as[String] shouldBe "web"
    (json \ "ports").toOption shouldBe None
    (json \ "clusterIP").toOption shouldBe None
    (json \ "type").toOption shouldBe None
  }

  it should "support withType and isHeadless" in {
    val spec = ServiceSpecApplyConfig().withType(Service.Type.LoadBalancer)
    spec._type shouldBe Some(Service.Type.LoadBalancer)

    val headless = ServiceSpecApplyConfig().isHeadless
    headless.clusterIP shouldBe Some("None")
  }

  it should "support exposeOnPort" in {
    val spec = ServiceSpecApplyConfig()
      .exposeOnPort(Service.Port(port = 80))
      .exposeOnPort(Service.Port(port = 443))
    spec.ports.get.size shouldBe 2
  }
}
