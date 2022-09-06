package skuber.networking

import org.specs2.mutable.Specification
import play.api.libs.json.Json
import skuber.LabelSelector.dsl._
import skuber._
import NetworkPolicy._
import scala.language.reflectiveCalls

/**
 * @author David O'Riordan
 */
class NetworkPolicySpec extends Specification {
  "This is a unit specification for the skuber NetworkPolicy class. ".txt

  val networkPolicyExample: NetworkPolicy = {
    val podSelector=LabelSelector("role" is "db")

    val fromIpBlock=IPBlock(cidr="172.17.0.0/16", except=List("172.17.1.0/24"))
    val fromNsSelector=LabelSelector("project" is "myproject")
    val fromPodSelector=LabelSelector("role" is "frontend")
    val fromPeers=List(Peer(ipBlock=Some(fromIpBlock)),
      Peer(namespaceSelector = Some(fromNsSelector)),
      Peer(podSelector = Some(fromPodSelector)))
    val fromPorts=List(Port(639))
    val ingressRule=IngressRule(from=fromPeers, ports=fromPorts)

    val toPeers=List(Peer(ipBlock=Some(IPBlock(cidr="10.0.0.0/24"))))
    val toPorts=List(Port(5978))
    val egressRule=EgressRule(ports=toPorts, to=toPeers)

    NetworkPolicy("test-network-policy")
      .inNamespace(Namespace.default.name)
      .selectPods(podSelector)
      .applyIngressPolicy
      .allowIngress(ingressRule)
      .applyEgressPolicy
      .allowEgress(egressRule)
  }


  val networkPolicyExampleStr=
  """{
      |"apiVersion": "networking.k8s.io/v1",
      |"kind": "NetworkPolicy"
      |"metadata" {
      |  "name": "test-network-policy"
      |  "namespace": "default"
      |}
      |"spec" {
      |  "podSelector" {
      |    "matchLabels": {
      |      "role": "db"
      |  },
      |  "policyTypes": [
      |    "Ingress",
      |    "Egress"
      |  ],
      |  "ingress": [
      |    {
      |      "from": [{
      |        "ipBlock" : {
      |          "cidr: "172.17.0.0/16"
      |          "except": ["172.17.1.0/24"]
      |        },
      |        "namespaceSelector": {
      |           "matchLabels": {
      |             "project": "myproject"
      |           }
      |         },
      |         "podSelector": {
      |            "matchLabels": {
      |              "role": "frontend"
      |            }
      |          }
      |        }],
      |      "ports": [
      |        {
      |          "protocol": "TCP"
      |          "port": 6379
      |        }
      |      ]
      |    }
      |  ]
      |  "egress": [
      |    {
      |      "to": [{
      |        "ipBlock": {
      |          "cidr": "10.0.0.0/24"
      |        }
      |      }],
      |      "ports": [{
      |        "protocol": "TCP",
      |        "port": 5978
      |      }]
      |    }
      |  ]
      |}
      |}
   """.stripMargin

  "A NetworkPolicy object can be written to Json and read back again" >> {
    val json=Json.toJson(networkPolicyExample)
    val jsonStr = json.toString
    val examplePolicy=Json.fromJson[NetworkPolicy](json).get
    examplePolicy mustEqual networkPolicyExample
  }
}