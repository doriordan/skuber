package skuber.model.ac.rbac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.authorization.rbac._
import skuber.json.format._
import skuber.json.authorization.rbac.format._

class ClusterRoleBindingApplyConfigSpec extends AnyFlatSpec with Matchers {

  "ClusterRoleBindingApplyConfig" should "be constructed by name" in {
    val crb = ClusterRoleBindingApplyConfig("my-cluster-binding")
    crb.name shouldBe "my-cluster-binding"
    crb.kind shouldBe "ClusterRoleBinding"
    crb.apiVersion shouldBe "rbac.authorization.k8s.io/v1"
  }

  it should "serialize with roleRef and subjects" in {
    val crb = ClusterRoleBindingApplyConfig("my-cluster-binding")
      .withRoleRef(RoleRef("rbac.authorization.k8s.io", "ClusterRole", "my-clusterrole"))
      .addSubject(Subject(Some("rbac.authorization.k8s.io/v1"), "ServiceAccount", "default", Some("kube-system")))
    val json = Json.toJson(crb)
    (json \ "kind").as[String] shouldBe "ClusterRoleBinding"
    (json \ "roleRef" \ "name").as[String] shouldBe "my-clusterrole"
    (json \ "subjects")(0).as[play.api.libs.json.JsValue].\("name").as[String] shouldBe "default"
  }

  it should "extend ApplyConfiguration[ClusterRoleBinding]" in {
    val crb: ApplyConfiguration[ClusterRoleBinding] = ClusterRoleBindingApplyConfig("my-cluster-binding")
    crb.name shouldBe "my-cluster-binding"
  }
}
