package skuber.model

import org.specs2.mutable.Specification
import LabelSelector.dsl._

import scala.language.{postfixOps, reflectiveCalls}

/**
  * @author David O'Riordan
  */
class LabelSelectorSpec extends Specification {
  "A label selector can be constructed" >> {
    "from a label existence requirement" >> {
      val sel = LabelSelector("production")
      sel.requirements.size must beEqualTo(1)
      sel.requirements.head must beEqualTo(LabelSelector.ExistsRequirement("production"))
    }
  }

  "A label selector can be constructed" >> {
    "from a label equality requirement" >> {
      val sel = LabelSelector("env" is "production")
      sel.requirements.size must beEqualTo(1)
      sel.requirements.head must beEqualTo(LabelSelector.IsEqualRequirement("env", "production"))
    }
  }

  "A label selector can be constructed" >> {
    "from a label inequality requirement" >> {
      val sel = LabelSelector("env" isNot "production")
      sel.requirements.size must beEqualTo(1)
      sel.requirements.head must beEqualTo(LabelSelector.IsNotEqualRequirement("env", "production"))
    }
  }

  "A label selector can be constructed" >> {
    "from a 'In' set requirement" >> {
      val sel = LabelSelector("env" isIn List("production", "staging"))
      sel.requirements.size must beEqualTo(1)
      sel.requirements.head must beEqualTo(LabelSelector.InRequirement("env", List("production", "staging")))
    }
  }

  "A label selector can be constructed" >> {
    "from a 'NotIn' set requirement" >> {
      val sel = LabelSelector("env" isNotIn List("production", "staging"))
      sel.requirements.size must beEqualTo(1)
      sel.requirements.head must beEqualTo(LabelSelector.NotInRequirement("env", List("production", "staging")))
    }
  }

  "A label selector can be constructed" >> {
    "from a mixed equality and set based requirement" >> {
      val sel = LabelSelector("tier" is "frontend", "env" isNotIn List("production", "staging"))
      sel.requirements.size must beEqualTo(2)
      sel.requirements.head must beEqualTo(LabelSelector.IsEqualRequirement("tier", "frontend"))
      sel.requirements(1) must beEqualTo(LabelSelector.NotInRequirement("env", List("production", "staging")))
    }
  }

  "A label selector can be constructed" >> {
    "from multiple requirements" >> {
      val sel = LabelSelector(
        "tier" is "frontend",
        "release" doesNotExist,
        "env" isNotIn List("production", "staging"))
      sel.requirements.size must beEqualTo(3)
      sel.requirements.head must beEqualTo(LabelSelector.IsEqualRequirement("tier", "frontend"))
      sel.requirements(1) must beEqualTo(LabelSelector.NotExistsRequirement("release"))
      sel.requirements(2) must beEqualTo(LabelSelector.NotInRequirement("env", List("production", "staging")))
    }
  }
}
