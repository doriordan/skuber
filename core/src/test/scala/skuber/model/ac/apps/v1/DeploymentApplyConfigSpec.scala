package skuber.model.ac.apps.v1

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.apps.v1.Deployment
import skuber.json.format._

class DeploymentApplyConfigSpec extends AnyFlatSpec with Matchers {

  "DeploymentApplyConfig" should "be constructed by name" in {
    val depl = DeploymentApplyConfig("nginx-deployment")
    depl.name shouldBe "nginx-deployment"
    depl.kind shouldBe "Deployment"
    depl.apiVersion shouldBe "apps/v1"
  }

  it should "serialize the full Deployment example from the spec" in {
    val depl = DeploymentApplyConfig("nginx-deployment")
      .addLabel("app" -> "nginx")
      .withSpec(DeploymentSpecApplyConfig()
        .withReplicas(3)
        .withSelector(LabelSelector(LabelSelector.IsEqualRequirement("app", "nginx")))
        .withTemplate(PodTemplateSpecApplyConfig()
          .addLabel("app" -> "nginx")
          .addContainer(ContainerApplyConfig("nginx", "nginx:1.25")
            .exposePort(80)
            .limitMemory("128Mi")
            .limitCPU("500m")
          )
        )
      )

    val json = Json.toJson(depl)
    (json \ "kind").as[String] shouldBe "Deployment"
    (json \ "apiVersion").as[String] shouldBe "apps/v1"
    (json \ "metadata" \ "name").as[String] shouldBe "nginx-deployment"
    (json \ "metadata" \ "labels" \ "app").as[String] shouldBe "nginx"
    (json \ "spec" \ "replicas").as[Int] shouldBe 3
    (json \ "spec" \ "template" \ "metadata" \ "labels" \ "app").as[String] shouldBe "nginx"

    val container = (json \ "spec" \ "template" \ "spec" \ "containers")(0)
    (container \ "name").as[String] shouldBe "nginx"
    (container \ "image").as[String] shouldBe "nginx:1.25"
    (container \ "ports")(0).\("containerPort").as[Int] shouldBe 80
    (container \ "resources" \ "limits" \ "memory").as[String] shouldBe "128Mi"
    (container \ "resources" \ "limits" \ "cpu").as[String] shouldBe "500m"
  }

  it should "omit spec when not set" in {
    val depl = DeploymentApplyConfig("test")
    val json = Json.toJson(depl)
    (json \ "spec").toOption shouldBe None
  }

  it should "extend ApplyConfiguration[Deployment]" in {
    val depl: ApplyConfiguration[Deployment] = DeploymentApplyConfig("test")
    depl.name shouldBe "test"
  }

  it should "support withReplicas shortcut" in {
    val depl = DeploymentApplyConfig("test").withReplicas(5)
    depl.spec.get.replicas shouldBe Some(5)
  }

  "DeploymentSpecApplyConfig" should "serialize only set fields" in {
    val spec = DeploymentSpecApplyConfig().withReplicas(3)
    val json = Json.toJson(spec)(DeploymentSpecApplyConfig.writes)
    (json \ "replicas").as[Int] shouldBe 3
    (json \ "selector").toOption shouldBe None
    (json \ "template").toOption shouldBe None
    (json \ "strategy").toOption shouldBe None
  }

  it should "serialize strategy when set" in {
    val spec = DeploymentSpecApplyConfig()
      .withStrategy(Deployment.Strategy(Deployment.StrategyType.Recreate))
    val json = Json.toJson(spec)(DeploymentSpecApplyConfig.writes)
    (json \ "strategy" \ "type").as[String] shouldBe "Recreate"
  }
}
