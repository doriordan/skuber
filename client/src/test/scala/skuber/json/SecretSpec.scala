package skuber.json

import org.specs2.mutable.Specification
import skuber.{ObjectMeta, Secret} // for unit-style testing
import format._

import play.api.libs.json._

/**
  * @author Cory Klein
  */
class SecretSpec extends Specification {

  "A Secret containing a byte array can symmetrically be written to json and the same value read back in" >> {
    val dataBytes = "hello".getBytes
    val secret = Secret(metadata = ObjectMeta("mySecret"), data = Map("key" -> dataBytes))
    val resultBytes = Json.fromJson[Secret](Json.toJson(secret)).get.data("key")
    dataBytes mustEqual resultBytes
  }
  "this can be done with an empty data map" >> {
    val mySecret = Secret(metadata = ObjectMeta("mySecret"))
    val json = Json.toJson(mySecret)
    val readSecret = Json.fromJson[Secret](Json.toJson(mySecret)).get
    mySecret mustEqual readSecret
  }
  "this can be done with immutable defined" >> {
    val mySecret = Secret(metadata = ObjectMeta("mySecret"), immutable = true)
    val readSecret = Json.fromJson[Secret](Json.toJson(mySecret)).get
    mySecret mustEqual readSecret
    readSecret.immutable mustEqual true
  }
  "this can be done without immutable defined" >> {
    val mySecret = Secret(metadata = ObjectMeta("mySecret"))
    val readSecret = Json.fromJson[Secret](Json.toJson(mySecret)).get
    mySecret mustEqual readSecret
    readSecret.immutable mustEqual false
  }
  "this can be done with the type member defined" >> {
    val mySecret = Secret(metadata = ObjectMeta("mySecret"), `type` = "myType")
    val readSecret = Json.fromJson[Secret](Json.toJson(mySecret)).get
    mySecret mustEqual readSecret
  }
}
