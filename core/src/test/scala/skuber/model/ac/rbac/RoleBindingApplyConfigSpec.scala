package skuber.model.ac.rbac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.authorization.rbac._
import skuber.json.format._
import skuber.json.authorization.rbac.format._

class RoleBindingApplyConfigSpec extends AnyFlatSpec with Matchers {

  "RoleBindingApplyConfig" should "be constructed by name" in {
    val rb = RoleBindingApplyConfig("my-binding")
    rb.name shouldBe "my-binding"
    rb.kind shouldBe "RoleBinding"
    rb.apiVersion shouldBe "rbac.authorization.k8s.io/v1"
  }

  it should "serialize with roleRef and subjects" in {
    val rb = RoleBindingApplyConfig("my-binding")
      .withRoleRef(RoleRef("rbac.authorization.k8s.io", "Role", "my-role"))
      .addSubject(Subject(Some("rbac.authorization.k8s.io/v1"), "User", "jane", None))
    val json = Json.toJson(rb)
    (json \ "kind").as[String] shouldBe "RoleBinding"
    (json \ "roleRef" \ "name").as[String] shouldBe "my-role"
    (json \ "subjects")(0).as[play.api.libs.json.JsValue].\("name").as[String] shouldBe "jane"
  }

  it should "extend ApplyConfiguration[RoleBinding]" in {
    val rb: ApplyConfiguration[RoleBinding] = RoleBindingApplyConfig("my-binding")
    rb.name shouldBe "my-binding"
  }
}
