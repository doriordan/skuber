package skuber.model.ac.networking

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.networking.Ingress
import skuber.json.format._
import skuber.json.networking.format._

class IngressApplyConfigSpec extends AnyFlatSpec with Matchers {

  "IngressApplyConfig" should "be constructed by name" in {
    val ing = IngressApplyConfig("my-ingress")
    ing.name shouldBe "my-ingress"
    ing.kind shouldBe "Ingress"
    ing.apiVersion shouldBe "networking.k8s.io/v1beta1"
  }

  it should "serialize with spec fields" in {
    val ing = IngressApplyConfig("my-ingress")
      .withSpec(IngressSpecApplyConfig()
        .withIngressClassName("nginx")
        .addRule(Ingress.Rule(
          host = Some("example.com"),
          http = Ingress.HttpRule(List(
            Ingress.Path("/", None, Ingress.Backend("my-service", Left(80)))
          ))
        ))
      )
    val json = Json.toJson(ing)
    (json \ "kind").as[String] shouldBe "Ingress"
    (json \ "spec" \ "ingressClassName").as[String] shouldBe "nginx"
    (json \ "spec" \ "rules").as[List[play.api.libs.json.JsValue]].size shouldBe 1
  }

  it should "extend ApplyConfiguration[Ingress]" in {
    val ing: ApplyConfiguration[Ingress] = IngressApplyConfig("my-ingress")
    ing.name shouldBe "my-ingress"
  }

  it should "support TLS configuration" in {
    val spec = IngressSpecApplyConfig()
      .addTLS(Ingress.TLS(hosts = List("example.com"), secretName = Some("tls-secret")))
    spec.tls.get.size shouldBe 1
  }
}
