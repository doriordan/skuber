package skuber.json

import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.LabelSelector.dsl._
import skuber._
import skuber.json.format.MaybeEmpty

/**
 * @author Chris Baker
 */
class FormatMaybeSpec extends Specification {

  "formatMaybeEmptyInt squashes zero but nothing else" >> {
    val wrt = new MaybeEmpty(JsPath \ "test").formatMaybeEmptyInt()

    (wrt.writes(0) \ "test").asOpt[Int] must beNone
    (wrt.writes(1) \ "test").asOpt[Int] must beSome(1)
  }

}