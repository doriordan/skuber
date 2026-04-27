package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class LimitRangeApplyConfigSpec extends AnyFlatSpec with Matchers {

  "LimitRangeApplyConfig" should "be constructed by name" in {
    val lr = LimitRangeApplyConfig("my-limits")
    lr.name shouldBe "my-limits"
    lr.kind shouldBe "LimitRange"
    lr.apiVersion shouldBe "v1"
  }

  it should "serialize with items" in {
    val lr = LimitRangeApplyConfig("my-limits")
      .withSpec(LimitRangeSpecApplyConfig()
        .addItem(LimitRange.Item(
          _type = Some(LimitRange.ItemType.Container),
          default = Map(Resource.memory -> Resource.Quantity("256Mi")),
          defaultRequest = Map(Resource.memory -> Resource.Quantity("128Mi"))
        ))
      )
    val json = Json.toJson(lr)
    (json \ "kind").as[String] shouldBe "LimitRange"
    (json \ "spec" \ "limits").as[List[play.api.libs.json.JsValue]].size shouldBe 1
  }

  it should "extend ApplyConfiguration[LimitRange]" in {
    val lr: ApplyConfiguration[LimitRange] = LimitRangeApplyConfig("my-limits")
    lr.name shouldBe "my-limits"
  }
}
