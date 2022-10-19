package skuber

/**
  * @author David O'Riordan
  */

case class LabelSelector(requirements: LabelSelector.Requirement*) {
  override def toString=requirements.mkString(",")

  override def equals(o: Any) = o match {
    case that: LabelSelector => that.requirements.sortBy(r => r.key).equals(requirements.sortBy(r => r.key))
    case _ => false
  }
}

object LabelSelector {

  sealed trait Requirement {
    val key: String
  }

  sealed trait ExistenceRequirement extends Requirement

  sealed trait EqualityRequirement extends Requirement {
    val value: String
  }
  sealed trait SetRequirement extends Requirement {
    val values: List[String]
    def valuesAsString="(" + values.mkString(",") + ")"
  }

  case class ExistsRequirement(key: String) extends ExistenceRequirement {
    override def toString=key
  }

  case class NotExistsRequirement(key: String) extends Requirement {
    override def toString="!"+key
  }

  case class IsEqualRequirement(key: String, value: String) extends EqualityRequirement {
    override def toString=key+"="+value
  }

  case class IsNotEqualRequirement(key: String, value: String) extends EqualityRequirement {
    override def toString=key+"!="+value
  }

  case class InRequirement(key: String, values: List[String]) extends SetRequirement {
    override def toString=key+" in "+valuesAsString
  }
  case class NotInRequirement(key: String, values: List[String]) extends SetRequirement {
    override def toString=key+" notin "+valuesAsString
  }

  object dsl {
    // this little DSL enables equality and set based selector requirements to be specified using a simple syntax
    // analogous to the native k8s one, with renamed operators to avoid confusion with standard/common Scala ops.
    // The following illustrates mappings from this DSL to k8s selector requirements syntax:
    // "production" -> "production"
    // '"production" doesNotExist ->  "!production"
    // "tier" is "frontend" -> "tier=frontend"
    // "status" isNot "release" -> "status!=release"
    // "env" isIn List("staging", "production") -> "env in (staging,release)"
    // "env" isNotIn List("local", "dev") -> "env notin (local,dev)"
    implicit def strToReq(key: String) = new LabelSelector.ExistsRequirement(key) {
      def doesNotExist = NotExistsRequirement(key)
      def is(value: String) = IsEqualRequirement(key, value)
      def isNot(value: String) = IsNotEqualRequirement(key,value)
      def isIn(values: List[String]) = InRequirement(key, values)
      def isNotIn(values: List[String]) = NotInRequirement(key, values)
    }
    implicit def reqToSel(req: LabelSelector.Requirement) = LabelSelector(req)
  }
}






