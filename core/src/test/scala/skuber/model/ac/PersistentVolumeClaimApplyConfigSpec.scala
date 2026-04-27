package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class PersistentVolumeClaimApplyConfigSpec extends AnyFlatSpec with Matchers {

  "PersistentVolumeClaimApplyConfig" should "be constructed by name" in {
    val pvc = PersistentVolumeClaimApplyConfig("my-pvc")
    pvc.name shouldBe "my-pvc"
    pvc.kind shouldBe "PersistentVolumeClaim"
    pvc.apiVersion shouldBe "v1"
  }

  it should "serialize with spec fields" in {
    val pvc = PersistentVolumeClaimApplyConfig("my-pvc")
      .withSpec(PersistentVolumeClaimSpecApplyConfig()
        .withAccessModes(List(PersistentVolume.AccessMode.ReadWriteOnce))
        .withStorageClassName("standard")
        .withStorageRequest("10Gi")
      )
    val json = Json.toJson(pvc)
    (json \ "kind").as[String] shouldBe "PersistentVolumeClaim"
    (json \ "spec" \ "accessModes")(0).as[String] shouldBe "ReadWriteOnce"
    (json \ "spec" \ "storageClassName").as[String] shouldBe "standard"
    (json \ "spec" \ "resources" \ "requests" \ "storage").as[String] shouldBe "10Gi"
  }

  it should "omit spec when not set" in {
    val pvc = PersistentVolumeClaimApplyConfig("my-pvc")
    val json = Json.toJson(pvc)
    (json \ "spec").toOption shouldBe None
  }

  it should "extend ApplyConfiguration[PersistentVolumeClaim]" in {
    val pvc: ApplyConfiguration[PersistentVolumeClaim] = PersistentVolumeClaimApplyConfig("my-pvc")
    pvc.name shouldBe "my-pvc"
  }
}
