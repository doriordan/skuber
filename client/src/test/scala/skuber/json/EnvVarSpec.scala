package skuber.json

import org.specs2.mutable.Specification // for unit-style testing
import org.specs2.execute.Result
import org.specs2.execute.Failure
import org.specs2.execute.Success

import scala.math.BigInt

import java.util.Calendar

import skuber.EnvVar
import format._

import play.api.libs.json._

class EnvVarSpec extends Specification {
  "This is a unit specification for the skuber formatter for env vars.\n ".txt

  // EnvVar reader and writer
  "An EnvVar can be read from json\n" >> {
    "this can be done for an env var with a field ref with a field path" >> {
      val env1 = Json.fromJson[EnvVar](Json.parse("""
          |{
          |  "name": "PODNAME",
          |  "valueFrom" : {
          |     "fieldRef": {
          |       "fieldPath": "metadata.name"
          |     }
          |  }
          |}
        """.stripMargin)).get

      val env2 = EnvVar("PODNAME", EnvVar.FieldRef("metadata.name"))

      env1 mustEqual env2
    }
  }
}
