package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class ReplicationControllerApplyConfigSpec extends AnyFlatSpec with Matchers {

  "ReplicationControllerApplyConfig" should "be constructed by name" in {
    val rc = ReplicationControllerApplyConfig("my-rc")
    rc.name shouldBe "my-rc"
    rc.kind shouldBe "ReplicationController"
    rc.apiVersion shouldBe "v1"
  }

  it should "serialize with spec fields" in {
    val rc = ReplicationControllerApplyConfig("my-rc")
      .withSpec(ReplicationControllerSpecApplyConfig()
        .withReplicas(3)
        .withSelector(Map("app" -> "web"))
        .withTemplate(PodTemplateSpecApplyConfig()
          .addLabel("app" -> "web")
          .addContainer(ContainerApplyConfig("nginx", "nginx:1.25"))
        )
      )
    val json = Json.toJson(rc)
    (json \ "kind").as[String] shouldBe "ReplicationController"
    (json \ "spec" \ "replicas").as[Int] shouldBe 3
    (json \ "spec" \ "selector" \ "app").as[String] shouldBe "web"
  }

  it should "extend ApplyConfiguration[ReplicationController]" in {
    val rc: ApplyConfiguration[ReplicationController] = ReplicationControllerApplyConfig("my-rc")
    rc.name shouldBe "my-rc"
  }
}
