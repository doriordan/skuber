package skuber.model.ac.rbac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.authorization.rbac._
import skuber.json.format._
import skuber.json.authorization.rbac.format._

class ClusterRoleApplyConfigSpec extends AnyFlatSpec with Matchers {

  "ClusterRoleApplyConfig" should "be constructed by name" in {
    val cr = ClusterRoleApplyConfig("my-clusterrole")
    cr.name shouldBe "my-clusterrole"
    cr.kind shouldBe "ClusterRole"
    cr.apiVersion shouldBe "rbac.authorization.k8s.io/v1"
  }

  it should "serialize with rules" in {
    val cr = ClusterRoleApplyConfig("my-clusterrole")
      .addRule(PolicyRule(
        apiGroups = List(""),
        attributeRestrictions = None,
        nonResourceURLs = Nil,
        resourceNames = Nil,
        resources = List("nodes"),
        verbs = List("get", "list")
      ))
    val json = Json.toJson(cr)
    (json \ "kind").as[String] shouldBe "ClusterRole"
    (json \ "rules").as[List[play.api.libs.json.JsValue]].size shouldBe 1
  }

  it should "extend ApplyConfiguration[ClusterRole]" in {
    val cr: ApplyConfiguration[ClusterRole] = ClusterRoleApplyConfig("my-clusterrole")
    cr.name shouldBe "my-clusterrole"
  }
}
