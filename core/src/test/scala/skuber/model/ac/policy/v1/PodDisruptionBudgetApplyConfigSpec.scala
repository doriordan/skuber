package skuber.model.ac.policy.v1

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.policy.v1.PodDisruptionBudget
import skuber.json.format._

class PodDisruptionBudgetApplyConfigSpec extends AnyFlatSpec with Matchers {

  "PodDisruptionBudgetApplyConfig" should "be constructed by name" in {
    val pdb = PodDisruptionBudgetApplyConfig("my-pdb")
    pdb.name shouldBe "my-pdb"
    pdb.kind shouldBe "PodDisruptionBudget"
    pdb.apiVersion shouldBe "policy/v1"
  }

  it should "serialize with spec fields" in {
    val pdb = PodDisruptionBudgetApplyConfig("my-pdb")
      .withSpec(PodDisruptionBudgetSpecApplyConfig()
        .withMinAvailable(Left(2))
        .withSelector(LabelSelector(LabelSelector.IsEqualRequirement("app", "web")))
      )
    val json = Json.toJson(pdb)
    (json \ "kind").as[String] shouldBe "PodDisruptionBudget"
    (json \ "apiVersion").as[String] shouldBe "policy/v1"
    (json \ "spec" \ "minAvailable").as[Int] shouldBe 2
  }

  it should "extend ApplyConfiguration[PodDisruptionBudget]" in {
    val pdb: ApplyConfiguration[PodDisruptionBudget] = PodDisruptionBudgetApplyConfig("my-pdb")
    pdb.name shouldBe "my-pdb"
  }

  it should "support maxUnavailable as percentage" in {
    val spec = PodDisruptionBudgetSpecApplyConfig()
      .withMaxUnavailable(Right("25%"))
    spec.maxUnavailable shouldBe Some(Right("25%"))
  }
}
