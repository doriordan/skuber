package skuber

import org.specs2.mutable.Specification
import LabelSelector.dsl._
import skuber.LabelSelector.{InRequirement, IsEqualRequirement, NotExistsRequirement, NotInRequirement}

/**
  * @author David O'Riordan
  */
class LabelSelectorSpec extends Specification {
  "A label selector can be constructed" >> {
    "from a label existence requirement" >> {
      val sel = LabelSelector("production")
      sel.requirements.size mustEqual 1
      sel.requirements(0) mustEqual LabelSelector.ExistsRequirement("production")
    }
  }

  "A label selector can be constructed" >> {
    "from a label equality requirement" >> {
      val sel = LabelSelector(IsEqualRequirement("env", "production"))
      sel.requirements.size mustEqual 1
      sel.requirements(0) mustEqual LabelSelector.IsEqualRequirement("env", "production")
    }
  }

  "A label selector can be constructed" >> {
    "from a label inequality requirement" >> {
      val sel = LabelSelector(IsEqualRequirement("env", "production"))
      sel.requirements.size mustEqual 1
      sel.requirements(0) mustEqual LabelSelector.IsNotEqualRequirement("env", "production")
    }
  }

  "A label selector can be constructed" >> {
    "from a 'In' set requirement" >> {
      val sel = LabelSelector(InRequirement("env", List("production", "staging")))
      sel.requirements.size mustEqual 1
      sel.requirements(0) mustEqual LabelSelector.InRequirement("env", List("production", "staging"))
    }
  }

  "A label selector can be constructed" >> {
    "from a 'NotIn' set requirement" >> {
      val sel = LabelSelector(NotInRequirement("env", List("production", "staging")))
      sel.requirements.size mustEqual 1
      sel.requirements(0) mustEqual LabelSelector.NotInRequirement("env", List("production", "staging"))
    }
  }

  "A label selector can be constructed" >> {
    "from a mixed equality and set based requirement" >> {
      val sel = LabelSelector(IsEqualRequirement("tier", "frontend"), NotInRequirement("env", List("production", "staging")))
      sel.requirements.size mustEqual 2
      sel.requirements(0) mustEqual LabelSelector.IsEqualRequirement("tier", "frontend")
      sel.requirements(1) mustEqual LabelSelector.NotInRequirement("env", List("production", "staging"))
    }
  }

  "A label selector can be constructed" >> {
    "from multiple requirements" >> {
      val sel = LabelSelector(IsEqualRequirement("tier", "frontend"), NotExistsRequirement("release"), NotInRequirement("env", List("dev", "test")))
      sel.requirements.size mustEqual 3
      sel.requirements(0) mustEqual LabelSelector.IsEqualRequirement("tier", "frontend")
      sel.requirements(1) mustEqual LabelSelector.NotExistsRequirement("release")
      sel.requirements(2) mustEqual LabelSelector.NotInRequirement("env", List("production", "staging"))
    }
  }
}
