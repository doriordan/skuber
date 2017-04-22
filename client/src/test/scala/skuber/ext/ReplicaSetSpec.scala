package skuber.ext

import org.specs2.mutable.Specification // for unit-style testing

import scala.math.BigInt

import skuber._
import skuber.LabelSelector.dsl._

import skuber.json.ext.format._

import play.api.libs.json._

/**
  * @author Chris Baker
  */
class ReplicaSetSpec extends Specification {
  "This is a unit specification for the skuber ReplicaSet class. ".txt

  "A ReplicaSet object properly writes with zero replicas" >> {
    val rset=ReplicaSet("example").withReplicas(0)

    val writeRS = Json.toJson(rset)
    (writeRS \ "spec" \ "replicas").asOpt[Int] must beSome(0)
  }

}
