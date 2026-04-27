package skuber.model.ac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.json.format._

class EndpointsApplyConfigSpec extends AnyFlatSpec with Matchers {

  "EndpointsApplyConfig" should "be constructed by name" in {
    val ep = EndpointsApplyConfig("my-endpoints")
    ep.name shouldBe "my-endpoints"
    ep.kind shouldBe "Endpoints"
    ep.apiVersion shouldBe "v1"
  }

  it should "serialize with subsets" in {
    val ep = EndpointsApplyConfig("my-endpoints")
      .addSubset(Endpoints.Subset(
        addresses = List(Endpoints.Address("10.0.0.1")),
        notReadyAddresses = None,
        ports = List(Endpoints.Port(8080))
      ))
    val json = Json.toJson(ep)
    (json \ "kind").as[String] shouldBe "Endpoints"
    (json \ "subsets").as[List[play.api.libs.json.JsValue]].size shouldBe 1
  }

  it should "omit subsets when not set" in {
    val ep = EndpointsApplyConfig("my-endpoints")
    val json = Json.toJson(ep)
    (json \ "subsets").toOption shouldBe None
  }

  it should "extend ApplyConfiguration[Endpoints]" in {
    val ep: ApplyConfiguration[Endpoints] = EndpointsApplyConfig("my-endpoints")
    ep.name shouldBe "my-endpoints"
  }
}
