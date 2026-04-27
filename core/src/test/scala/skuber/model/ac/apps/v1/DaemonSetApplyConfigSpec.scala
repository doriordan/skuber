package skuber.model.ac.apps.v1

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.apps.v1.DaemonSet
import skuber.json.format._

class DaemonSetApplyConfigSpec extends AnyFlatSpec with Matchers {

  "DaemonSetApplyConfig" should "be constructed by name" in {
    val ds = DaemonSetApplyConfig("my-daemonset")
    ds.name shouldBe "my-daemonset"
    ds.kind shouldBe "DaemonSet"
    ds.apiVersion shouldBe "apps/v1"
  }

  it should "serialize with spec fields" in {
    val ds = DaemonSetApplyConfig("my-daemonset")
      .withSpec(DaemonSetSpecApplyConfig()
        .withSelector(LabelSelector(LabelSelector.IsEqualRequirement("app", "monitoring")))
        .withTemplate(PodTemplateSpecApplyConfig()
          .addLabel("app" -> "monitoring")
          .addContainer(ContainerApplyConfig("agent", "monitoring:latest"))
        )
      )
    val json = Json.toJson(ds)
    (json \ "kind").as[String] shouldBe "DaemonSet"
    (json \ "apiVersion").as[String] shouldBe "apps/v1"
    (json \ "spec" \ "template" \ "spec" \ "containers")(0).as[play.api.libs.json.JsValue].\("name").as[String] shouldBe "agent"
  }

  it should "extend ApplyConfiguration[DaemonSet]" in {
    val ds: ApplyConfiguration[DaemonSet] = DaemonSetApplyConfig("my-daemonset")
    ds.name shouldBe "my-daemonset"
  }
}
