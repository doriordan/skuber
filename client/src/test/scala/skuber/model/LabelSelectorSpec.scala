package skuber

//import org.specs2.mutable.Specification

import LabelSelector.dsl._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scala.language.{postfixOps, reflectiveCalls}

/**
 * @author David O'Riordan
 */
class LabelSelectorSpec extends AnyFunSpec with Matchers {
  describe("label selector tests ") {
    it("A label selector can be constructed1") {
      val sel = LabelSelector("production")
      sel.requirements.size shouldBe 1
      sel.requirements(0) shouldBe LabelSelector.ExistsRequirement("production")
    }

    it("A label selector can be constructed2") {
      val sel = LabelSelector("env" is "production")
      sel.requirements.size shouldBe 1
      sel.requirements(0) shouldBe LabelSelector.IsEqualRequirement("env", "production")
    }

    it("A label selector can be constructed3") {
      val sel = LabelSelector("env" isNot "production")
      sel.requirements.size shouldBe 1
      sel.requirements(0) shouldBe LabelSelector.IsNotEqualRequirement("env", "production")
    }

    it("A label selector can be constructed4") {
      val sel = LabelSelector("env" isIn List("production", "staging"))
      sel.requirements.size shouldBe 1
      sel.requirements(0) shouldBe LabelSelector.InRequirement("env", List("production", "staging"))
    }

    it("A label selector can be constructed5") {
      val sel = LabelSelector("env" isNotIn List("production", "staging"))
      sel.requirements.size shouldBe 1
      sel.requirements(0) shouldBe LabelSelector.NotInRequirement("env", List("production", "staging"))
    }

    it("A label selector can be constructed6") {
      val sel = LabelSelector("tier" is "frontend", "env" isNotIn List("production", "staging"))
      sel.requirements.size shouldBe 2
      sel.requirements(0) shouldBe LabelSelector.IsEqualRequirement("tier", "frontend")
      sel.requirements(1) shouldBe LabelSelector.NotInRequirement("env", List("production", "staging"))
    }

    it("A label selector can be constructed7") {
      val sel = LabelSelector(
        "tier" is "frontend",
        "release" doesNotExist,
        "env" isNotIn List("production", "staging"))
      sel.requirements.size shouldBe 3
      sel.requirements(0) shouldBe LabelSelector.IsEqualRequirement("tier", "frontend")
      sel.requirements(1) shouldBe LabelSelector.NotExistsRequirement("release")
      sel.requirements(2) shouldBe LabelSelector.NotInRequirement("env", List("production", "staging"))
    }
  }
}
