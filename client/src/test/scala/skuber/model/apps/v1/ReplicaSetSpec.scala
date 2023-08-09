package skuber.model.apps.v1

import org.specs2.mutable.Specification
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
