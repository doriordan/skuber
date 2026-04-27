package skuber.model.ac.apps.v1

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.apps.v1.ReplicaSet
import skuber.json.format._

class ReplicaSetApplyConfigSpec extends AnyFlatSpec with Matchers {

  "ReplicaSetApplyConfig" should "be constructed by name" in {
    val rs = ReplicaSetApplyConfig("my-rs")
    rs.name shouldBe "my-rs"
    rs.kind shouldBe "ReplicaSet"
    rs.apiVersion shouldBe "apps/v1"
  }

  it should "serialize with spec fields" in {
    val rs = ReplicaSetApplyConfig("my-rs")
      .withSpec(ReplicaSetSpecApplyConfig()
        .withReplicas(3)
        .withSelector(LabelSelector(LabelSelector.IsEqualRequirement("app", "web")))
        .withTemplate(PodTemplateSpecApplyConfig()
          .addLabel("app" -> "web")
          .addContainer(ContainerApplyConfig("nginx", "nginx:1.25"))
        )
      )
    val json = Json.toJson(rs)
    (json \ "kind").as[String] shouldBe "ReplicaSet"
    (json \ "apiVersion").as[String] shouldBe "apps/v1"
    (json \ "spec" \ "replicas").as[Int] shouldBe 3
  }

  it should "extend ApplyConfiguration[ReplicaSet]" in {
    val rs: ApplyConfiguration[ReplicaSet] = ReplicaSetApplyConfig("my-rs")
    rs.name shouldBe "my-rs"
  }
}
