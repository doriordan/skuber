package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class ContainerApplyConfigSpec extends AnyFlatSpec with Matchers {

  "ContainerApplyConfig" should "serialize only set fields" in {
    val container = ContainerApplyConfig(name = Some("nginx"), image = Some("nginx:1.25"))
    val json = Json.toJson(container)
    (json \ "name").as[String] shouldBe "nginx"
    (json \ "image").as[String] shouldBe "nginx:1.25"
    (json \ "ports").toOption shouldBe None
    (json \ "env").toOption shouldBe None
    (json \ "command").toOption shouldBe None
    (json \ "resources").toOption shouldBe None
  }

  it should "serialize ports when set" in {
    val container = ContainerApplyConfig("nginx", "nginx:1.25").exposePort(80)
    val json = Json.toJson(container)
    (json \ "ports")(0).as[Container.Port].containerPort shouldBe 80
  }

  it should "serialize env vars when set" in {
    val container = ContainerApplyConfig("nginx", "nginx:1.25").setEnvVar("HOME", "/root")
    val json = Json.toJson(container)
    (json \ "env")(0).as[EnvVar].name shouldBe "HOME"
  }

  it should "serialize resource limits when set" in {
    val container = ContainerApplyConfig("nginx", "nginx:1.25")
      .limitCPU("500m")
      .limitMemory("128Mi")
    val json = Json.toJson(container)
    (json \ "resources" \ "limits" \ "cpu").as[String] shouldBe "500m"
    (json \ "resources" \ "limits" \ "memory").as[String] shouldBe "128Mi"
  }

  "ContainerApplyConfig convenience constructor" should "set name and image" in {
    val c = ContainerApplyConfig("test", "image:latest")
    c.name shouldBe Some("test")
    c.image shouldBe Some("image:latest")
  }

  "ContainerApplyConfig fluent API" should "support exposePort with Port" in {
    val c = ContainerApplyConfig("test", "image:latest")
      .exposePort(Container.Port(containerPort = 8080, name = "http"))
    c.ports.get.head.containerPort shouldBe 8080
    c.ports.get.head.name shouldBe "http"
  }

  it should "support mount" in {
    val c = ContainerApplyConfig("test", "image:latest").mount("data", "/data", readOnly = true)
    c.volumeMounts.get.head.name shouldBe "data"
    c.volumeMounts.get.head.mountPath shouldBe "/data"
    c.volumeMounts.get.head.readOnly shouldBe true
  }

  it should "support withArgs and withEntrypoint" in {
    val c = ContainerApplyConfig("test", "image:latest").withEntrypoint("/bin/sh", "-c").withArgs("echo hello")
    c.command shouldBe Some(List("/bin/sh", "-c"))
    c.args shouldBe Some(List("echo hello"))
  }

  it should "support withImagePullPolicy" in {
    val c = ContainerApplyConfig("test", "image:latest").withImagePullPolicy(Container.PullPolicy.Always)
    c.imagePullPolicy shouldBe Some(Container.PullPolicy.Always)
  }

  it should "support resource requests" in {
    val c = ContainerApplyConfig("test", "image:latest")
      .requestCPU("250m")
      .requestMemory("64Mi")
    c.resources.get.requests("cpu").value shouldBe "250m"
    c.resources.get.requests("memory").value shouldBe "64Mi"
  }
}
