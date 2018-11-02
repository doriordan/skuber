package skuber

import org.specs2.mutable.Specification

import FieldSelector.dsl._


class FieldSelectorSpec extends Specification {
  "A field selector can be constructed" >> {
    "from a field equality requirement" >> {
      val sel = FieldSelector("env" is "production")
      sel.requirements.size mustEqual 1
      sel.requirements(0) mustEqual FieldSelector.IsEqualRequirement("env", "production")
    }
  }

  "A field selector can be constructed" >> {
    "from a field inequality requirement" >> {
      val sel = FieldSelector("env" isNot "production")
      sel.requirements.size mustEqual 1
      sel.requirements(0) mustEqual FieldSelector.IsNotEqualRequirement("env", "production")
    }
  }

  "A field selector can be constructed" >> {
    "from multiple requirements" >> {
      val sel = FieldSelector(
        "tier" is "frontend",
        "env" isNot "production")
      sel.requirements.size mustEqual 2
      sel.requirements(0) mustEqual FieldSelector.IsEqualRequirement("tier", "frontend")
      sel.requirements(1) mustEqual FieldSelector.IsNotEqualRequirement("env", "production")
    }
  }
}
