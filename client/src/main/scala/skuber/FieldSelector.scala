package skuber


case class FieldSelector(val requirements: FieldSelector.Requirement*) {
  override def toString = requirements.mkString(",")

  override def equals(o: Any) = o match {
    case that: FieldSelector => that.requirements.sortBy(r => r.key).equals(requirements.sortBy(r => r.key))
    case _ => false
  }
}


object FieldSelector {

  sealed trait Requirement {
    val key: String
  }

  case class IsEqualRequirement(key: String, value: String) extends Requirement {
    override def toString = key + "=" + value
  }

  case class IsNotEqualRequirement(key: String, value: String) extends Requirement {
    override def toString = key + "!=" + value
  }

  object dsl {

    // this little DSL enables equality selector requirements to be specified using a simple syntax
    // analogous to the native k8s one, with renamed operators to avoid confusion with standard/common Scala ops.
    // The following illustrates mappings from this DSL to k8s selector requirements syntax:
    // "tier" is "frontend" -> "tier=frontend"
    // "status" isNot "release" -> "status!=release"
    implicit class strToReq(key: String) {
      def is(value: String) = IsEqualRequirement(key, value)

      def isNot(value: String) = IsNotEqualRequirement(key, value)
    }

    implicit def reqToSel(req: FieldSelector.Requirement) = FieldSelector(req)
  }

}