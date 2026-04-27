package skuber.model.ac.rbac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.authorization.rbac._
import skuber.json.format._
import skuber.json.authorization.rbac.format._

class RoleApplyConfigSpec extends AnyFlatSpec with Matchers {

  "RoleApplyConfig" should "be constructed by name" in {
    val role = RoleApplyConfig("my-role")
    role.name shouldBe "my-role"
    role.kind shouldBe "Role"
    role.apiVersion shouldBe "rbac.authorization.k8s.io/v1"
  }

  it should "serialize with rules" in {
    val role = RoleApplyConfig("my-role")
      .addRule(PolicyRule(
        apiGroups = List(""),
        attributeRestrictions = None,
        nonResourceURLs = Nil,
        resourceNames = Nil,
        resources = List("pods"),
        verbs = List("get", "list", "watch")
      ))
    val json = Json.toJson(role)
    (json \ "kind").as[String] shouldBe "Role"
    (json \ "rules").as[List[play.api.libs.json.JsValue]].size shouldBe 1
  }

  it should "extend ApplyConfiguration[Role]" in {
    val role: ApplyConfiguration[Role] = RoleApplyConfig("my-role")
    role.name shouldBe "my-role"
  }
}
