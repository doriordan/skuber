package skuber.json

import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.model.settings.PodPreset

import scala.io.Source

/**
 * @author David O'Riordan
 */
class PodPresetFormatSpec extends Specification {
  "This is a unit specification for the skuber PodPreset related json formatter.\n ".txt

  "a podpreset can be read and written as json successfully" >> {
    val podPresetJsonSource=Source.fromURL(getClass.getResource("/examplePodPreset.json"))
    val podPresetJsonStr = podPresetJsonSource.mkString
    val podPreset = Json.parse(podPresetJsonStr).as[PodPreset]

    // write and read it back in again and compare
    val json = Json.toJson(podPreset)
    val readPodPreset = Json.fromJson[PodPreset](json).get
    readPodPreset mustEqual podPreset
  }

}
