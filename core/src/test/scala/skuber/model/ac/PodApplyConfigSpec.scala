package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class PodApplyConfigSpec extends AnyFlatSpec with Matchers {

  "PodSpecApplyConfig" should "serialize only set fields" in {
    val spec = PodSpecApplyConfig()
      .addContainer(ContainerApplyConfig("nginx", "nginx:1.25"))
      .withServiceAccountName("my-sa")
    val json = Json.toJson(spec)
    (json \ "containers").as[List[play.api.libs.json.JsValue]].size shouldBe 1
    (json \ "serviceAccountName").as[String] shouldBe "my-sa"
    (json \ "volumes").toOption shouldBe None
    (json \ "restartPolicy").toOption shouldBe None
    (json \ "nodeSelector").toOption shouldBe None
  }

  it should "support addVolume and addNodeSelector" in {
    val spec = PodSpecApplyConfig()
      .addVolume(Volume("data", Volume.EmptyDir()))
      .addNodeSelector("disktype" -> "ssd")
    spec.volumes.get.head.name shouldBe "data"
    spec.nodeSelector.get("disktype") shouldBe "ssd"
  }

  it should "support withRestartPolicy" in {
    val spec = PodSpecApplyConfig().withRestartPolicy(RestartPolicy.Never)
    spec.restartPolicy shouldBe Some(RestartPolicy.Never)
  }

  "PodApplyConfig" should "be constructed by name" in {
    val pod = PodApplyConfig("my-pod")
    pod.name shouldBe "my-pod"
    pod.kind shouldBe "Pod"
    pod.apiVersion shouldBe "v1"
  }

  it should "serialize with kind, apiVersion, and only set fields" in {
    val pod = PodApplyConfig("my-pod")
      .addLabel("app" -> "web")
      .withSpec(PodSpecApplyConfig()
        .addContainer(ContainerApplyConfig("nginx", "nginx:1.25").exposePort(80))
      )
    val json = Json.toJson(pod)
    (json \ "kind").as[String] shouldBe "Pod"
    (json \ "apiVersion").as[String] shouldBe "v1"
    (json \ "metadata" \ "name").as[String] shouldBe "my-pod"
    (json \ "metadata" \ "labels" \ "app").as[String] shouldBe "web"
    (json \ "spec" \ "containers")(0).as[play.api.libs.json.JsValue].\("name").as[String] shouldBe "nginx"
    (json \ "spec" \ "containers")(0).as[play.api.libs.json.JsValue].\("ports")(0).\("containerPort").as[Int] shouldBe 80
  }

  it should "extend ApplyConfiguration[Pod]" in {
    val pod: ApplyConfiguration[Pod] = PodApplyConfig("my-pod")
    pod.name shouldBe "my-pod"
  }

  it should "serialize empty spec fields as absent" in {
    val pod = PodApplyConfig("my-pod")
    val json = Json.toJson(pod)
    (json \ "spec").toOption shouldBe None
  }
}
