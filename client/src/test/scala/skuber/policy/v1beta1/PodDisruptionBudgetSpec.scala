package skuber.policy.v1beta1

import org.specs2.mutable.Specification
import play.api.libs.json.{JsSuccess, Json}
import skuber.LabelSelector.dsl._

class PodDisruptionBudgetSpec extends Specification {
  import PodDisruptionBudget._
  "A PodDisruptionBudget can" >> {
    "decoded from json" >> {
      val pdb = PodDisruptionBudget("someName")
        .withMaxUnavailable(Left(2))
        .withMinAvailable(Left(1))
        .withLabelSelector("application" is "someApplicationName")

      Json.parse(createJson("/examplePodDisruptionBudget.json")).validate[PodDisruptionBudget] mustEqual JsSuccess(pdb)
    }
    "encode to json" >> {
      Json.stringify(
        Json.toJson(
          PodDisruptionBudget("someName")
            .withMaxUnavailable(Left(2))
            .withMinAvailable(Left(1))
            .withLabelSelector("application" is "someApplicationName")
        )
      ) mustEqual createJson("/examplePodDisruptionBudget.json")
    }
  }

  private def createJson(file: String): String = {
    val source = scala.io.Source.fromURL(getClass.getResource(file))
    try source.mkString finally source.close()
  }
}
