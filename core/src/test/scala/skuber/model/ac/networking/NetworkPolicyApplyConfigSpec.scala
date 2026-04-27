package skuber.model.ac.networking

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.networking.NetworkPolicy
import skuber.json.format._

class NetworkPolicyApplyConfigSpec extends AnyFlatSpec with Matchers {

  "NetworkPolicyApplyConfig" should "be constructed by name" in {
    val np = NetworkPolicyApplyConfig("my-policy")
    np.name shouldBe "my-policy"
    np.kind shouldBe "NetworkPolicy"
    np.apiVersion shouldBe "networking.k8s.io/v1"
  }

  it should "serialize with spec fields" in {
    val np = NetworkPolicyApplyConfig("deny-all")
      .withSpec(NetworkPolicySpecApplyConfig()
        .withPodSelector(LabelSelector())
        .withPolicyTypes(List("Ingress"))
      )
    val json = Json.toJson(np)
    (json \ "kind").as[String] shouldBe "NetworkPolicy"
    (json \ "spec" \ "policyTypes")(0).as[String] shouldBe "Ingress"
  }

  it should "extend ApplyConfiguration[NetworkPolicy]" in {
    val np: ApplyConfiguration[NetworkPolicy] = NetworkPolicyApplyConfig("my-policy")
    np.name shouldBe "my-policy"
  }

  it should "support ingress and egress rules" in {
    val spec = NetworkPolicySpecApplyConfig()
      .withPodSelector(LabelSelector())
      .addIngressRule(NetworkPolicy.IngressRule(
        from = List(NetworkPolicy.Peer(namespaceSelector = Some(LabelSelector())))
      ))
      .addEgressRule(NetworkPolicy.EgressRule(
        to = List(NetworkPolicy.Peer(ipBlock = Some(NetworkPolicy.IPBlock("10.0.0.0/8"))))
      ))
    spec.ingress.get.size shouldBe 1
    spec.egress.get.size shouldBe 1
  }
}
