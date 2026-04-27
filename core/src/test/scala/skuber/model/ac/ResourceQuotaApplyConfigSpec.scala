package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class ResourceQuotaApplyConfigSpec extends AnyFlatSpec with Matchers {

  "ResourceQuotaApplyConfig" should "be constructed by name" in {
    val rq = ResourceQuotaApplyConfig("my-quota")
    rq.name shouldBe "my-quota"
    rq.kind shouldBe "ResourceQuota"
    rq.apiVersion shouldBe "v1"
  }

  it should "serialize with hard limits" in {
    val rq = ResourceQuotaApplyConfig("my-quota")
      .withSpec(ResourceQuotaSpecApplyConfig()
        .withHard(Map(Resource.pods -> Resource.Quantity("10"), Resource.cpu -> Resource.Quantity("4")))
      )
    val json = Json.toJson(rq)
    (json \ "kind").as[String] shouldBe "ResourceQuota"
    (json \ "spec" \ "hard" \ "pods").as[String] shouldBe "10"
    (json \ "spec" \ "hard" \ "cpu").as[String] shouldBe "4"
  }

  it should "omit spec when not set" in {
    val rq = ResourceQuotaApplyConfig("my-quota")
    val json = Json.toJson(rq)
    (json \ "spec").toOption shouldBe None
  }

  it should "extend ApplyConfiguration[Resource.Quota]" in {
    val rq: ApplyConfiguration[Resource.Quota] = ResourceQuotaApplyConfig("my-quota")
    rq.name shouldBe "my-quota"
  }
}
