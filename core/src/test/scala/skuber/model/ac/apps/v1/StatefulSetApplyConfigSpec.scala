package skuber.model.ac.apps.v1

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.apps.v1.StatefulSet
import skuber.json.format._

class StatefulSetApplyConfigSpec extends AnyFlatSpec with Matchers {

  "StatefulSetApplyConfig" should "be constructed by name" in {
    val sts = StatefulSetApplyConfig("my-sts")
    sts.name shouldBe "my-sts"
    sts.kind shouldBe "StatefulSet"
    sts.apiVersion shouldBe "apps/v1"
  }

  it should "serialize with spec fields" in {
    val sts = StatefulSetApplyConfig("my-sts")
      .withSpec(StatefulSetSpecApplyConfig()
        .withReplicas(3)
        .withServiceName("my-service")
        .withSelector(LabelSelector(LabelSelector.IsEqualRequirement("app", "db")))
        .withTemplate(PodTemplateSpecApplyConfig()
          .addLabel("app" -> "db")
          .addContainer(ContainerApplyConfig("postgres", "postgres:16"))
        )
      )
    val json = Json.toJson(sts)
    (json \ "kind").as[String] shouldBe "StatefulSet"
    (json \ "apiVersion").as[String] shouldBe "apps/v1"
    (json \ "spec" \ "replicas").as[Int] shouldBe 3
    (json \ "spec" \ "serviceName").as[String] shouldBe "my-service"
  }

  it should "omit spec when not set" in {
    val sts = StatefulSetApplyConfig("my-sts")
    val json = Json.toJson(sts)
    (json \ "spec").toOption shouldBe None
  }

  it should "extend ApplyConfiguration[StatefulSet]" in {
    val sts: ApplyConfiguration[StatefulSet] = StatefulSetApplyConfig("my-sts")
    sts.name shouldBe "my-sts"
  }

  it should "support volumeClaimTemplates" in {
    val spec = StatefulSetSpecApplyConfig()
      .addVolumeClaimTemplate(PersistentVolumeClaimApplyConfig("data")
        .withSpec(PersistentVolumeClaimSpecApplyConfig()
          .withAccessModes(List(PersistentVolume.AccessMode.ReadWriteOnce))
          .withStorageRequest("10Gi")
        )
      )
    spec.volumeClaimTemplates.get.size shouldBe 1
  }
}
