package skuber.json

import org.specs2.mutable.Specification
import org.specs2.execute.Result
import org.specs2.execute.Failure
import org.specs2.execute.Success

import scala.math.BigInt
import java.util.Calendar
import java.net.URL

import skuber._
import format._
import play.api.libs.json._

import scala.io.Source



/**
 * @author David O'Riordan
 */
class NodeFormatSpec extends Specification {
  "This is a unit specification for the skuber Node related json formatter.\n ".txt
  
import Node._
  
  // Node reader and writer
  "A Node can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for a simple Service with just a name" >> {
      val myNode = Node.named("myNode")
      val readNode = Json.fromJson[Node](Json.toJson(myNode)).get 
      myNode mustEqual readNode   
    }
    "this can be done for a simple Node with just a name and namespace set" >> {
      val myNode = Namespace("myNamespace").node("myNode")
      val readNode = Json.fromJson[Node](Json.toJson(myNode)).get 
      myNode mustEqual readNode
    }
    "this can be done for a Node with a simple Spec" >> {
      val myNS = Namespace("myNamespace")
      val myNode = myNS.node("myNode",
                             Spec(podCIDR="10.216.21.14/24"))
      val readNode = Json.fromJson[Node](Json.toJson(myNode)).get 
      myNode mustEqual readNode
    }
    "this can be done for a Node with a more complex spec" >> {
      
      val myNode=Namespace.default.node("myNode", Spec("10.124.23.56/24","myProvider",true, externalID="idext"))
      val readNode = Json.fromJson[Node](Json.toJson(myNode)).get 
      myNode mustEqual readNode
    }
    "an example json formatted node can be read" >> {
      val nodeJsonStr = """
              {
                  "kind": "Node",
                  "apiVersion": "v1",
                  "metadata": {
                    "name": "10.245.1.3",
                    "selfLink": "/api/v1/nodes/10.245.1.3",
                    "uid": "d94d0f69-4239-11e5-9586-0800279dd272",
                    "resourceVersion": "702",
                    "creationTimestamp": "2015-08-14T04:06:33Z",
                    "labels": {
                      "kubernetes.io/hostname": "10.245.1.3"
                    }
                  },
                  "spec": {
                    "externalID": "10.245.1.3",
                    "providerID": "vagrant://10.245.1.3"
                  },
                  "status": {
                    "capacity": {
                      "cpu": "2",
                      "memory": "1017552Ki",
                      "pods": "100"
                    },
                    "conditions": [
                      {
                        "type": "Ready",
                        "status": "True",
                        "lastHeartbeatTime": "2015-08-14T04:29:14Z",
                        "lastTransitionTime": "2015-08-14T04:06:32Z",
                        "reason": "kubelet is posting ready status"
                      }
                    ],
                    "addresses": [
                      {
                        "type": "LegacyHostIP",
                        "address": "10.245.1.3"
                      }
                    ],
                    "nodeInfo": {
                      "machineID": "6c30ad3b41fa4d1e89745cef6f22c4de",
                      "systemUUID": "3881F480-6EBA-4CE9-87A7-155D683184C1",
                      "bootID": "1acf7dfb-59b4-4081-b3d6-32bc4f1ab61e",
                      "kernelVersion": "3.17.4-301.fc21.x86_64",
                      "osImage": "Fedora 21 (Twenty One)",
                      "containerRuntimeVersion": "docker://1.6.2.fc21",
                      "kubeletVersion": "v0.19.3",
                      "kubeProxyVersion": "v0.19.3"
                    }
                  }
              }
        """
        val myNode = Json.parse(nodeJsonStr).as[Node]
        myNode.kind mustEqual "Node"
        myNode.spec mustNotEqual None
        val spec=myNode.spec.get
        spec.externalID mustEqual "10.245.1.3"
        spec.providerID mustEqual "vagrant://10.245.1.3"
        myNode.status mustNotEqual None
        val status = myNode.status.get
        status.addresses mustEqual List(Address("LegacyHostIP","10.245.1.3"))
        status.capacity.get("memory") mustEqual Some(Resource.Quantity("1017552Ki"))
        // write and read back in again, compare
        val readNode = Json.fromJson[Node](Json.toJson(myNode)).get 
        myNode mustEqual readNode
     }
     "an example json formatted nodelist can be read" >> {
           val nodesJsonStr = """
              {
                "kind": "NodeList",
                "apiVersion": "v1",
                "metadata": {
                  "selfLink": "/api/v1/nodes",
                  "resourceVersion": "26135"
                },
                "items": [
                  {
                    "metadata": {
                      "name": "10.245.1.3",
                      "selfLink": "/api/v1/nodes/10.245.1.3",
                      "uid": "d94d0f69-4239-11e5-9586-0800279dd272",
                      "resourceVersion": "26129",
                      "creationTimestamp": "2015-08-14T04:06:33Z",
                      "labels": {
                        "kubernetes.io/hostname": "10.245.1.3"
                      }
                    },
                    "spec": {
                      "externalID": "10.245.1.3",
                      "providerID": "vagrant://10.245.1.3"
                    },
                    "status": {
                      "capacity": {
                        "cpu": "2",
                        "memory": "1017552Ki",
                        "pods": "100"
                      },
                      "conditions": [
                        {
                          "type": "Ready",
                          "status": "True",
                          "lastHeartbeatTime": "2015-08-14T18:28:55Z",
                          "lastTransitionTime": "2015-08-14T18:27:12Z",
                          "reason": "kubelet is posting ready status"
                        }
                      ],
                      "addresses": [
                        {
                          "type": "LegacyHostIP",
                          "address": "10.245.1.3"
                        }
                      ],
                      "nodeInfo": {
                        "machineID": "6c30ad3b41fa4d1e89745cef6f22c4de",
                        "systemUUID": "3881F480-6EBA-4CE9-87A7-155D683184C1",
                        "bootID": "1acf7dfb-59b4-4081-b3d6-32bc4f1ab61e",
                        "kernelVersion": "3.17.4-301.fc21.x86_64",
                        "osImage": "Fedora 21 (Twenty One)",
                        "containerRuntimeVersion": "docker://1.6.2.fc21",
                        "kubeletVersion": "v0.19.3",
                        "kubeProxyVersion": "v0.19.3"
                      }
                    }
                  },
                  {
                    "metadata": {
                      "name": "10.245.1.4",
                      "selfLink": "/api/v1/nodes/10.245.1.4",
                      "uid": "ac59af8b-423a-11e5-9586-0800279dd272",
                      "resourceVersion": "26134",
                      "creationTimestamp": "2015-08-14T04:12:27Z",
                      "labels": {
                        "kubernetes.io/hostname": "10.245.1.4"
                      }
                    },
                    "spec": {
                      "externalID": "10.245.1.4",
                      "providerID": "vagrant://10.245.1.4"
                    },
                    "status": {
                      "capacity": {
                        "cpu": "2",
                        "memory": "1017552Ki",
                        "pods": "100"
                      },
                      "conditions": [
                        {
                          "type": "Ready",
                          "status": "True",
                          "lastHeartbeatTime": "2015-08-14T18:29:01Z",
                          "lastTransitionTime": "2015-08-14T16:20:35Z",
                          "reason": "kubelet is posting ready status"
                        }
                      ],
                      "addresses": [
                        {
                          "type": "LegacyHostIP",
                          "address": "10.245.1.4"
                        }
                      ],
                      "nodeInfo": {
                        "machineID": "6c30ad3b41fa4d1e89745cef6f22c4de",
                        "systemUUID": "5E243060-4FA9-45E4-A704-28228D288779",
                        "bootID": "3c356c3f-bb97-4dbe-bcea-fbf5f3c12a68",
                        "kernelVersion": "3.17.4-301.fc21.x86_64",
                        "osImage": "Fedora 21 (Twenty One)",
                        "containerRuntimeVersion": "docker://1.6.2.fc21",
                        "kubeletVersion": "v0.19.3",
                        "kubeProxyVersion": "v0.19.3"
                      }
                    }
                  },
                  {
                    "metadata": {
                      "name": "10.245.1.5",
                      "selfLink": "/api/v1/nodes/10.245.1.5",
                      "uid": "3d4c39f5-423b-11e5-9586-0800279dd272",
                      "resourceVersion": "26135",
                      "creationTimestamp": "2015-08-14T04:16:30Z",
                      "labels": {
                        "kubernetes.io/hostname": "10.245.1.5"
                      }
                    },
                    "spec": {
                      "externalID": "10.245.1.5",
                      "providerID": "vagrant://10.245.1.5"
                    },
                    "status": {
                      "capacity": {
                        "cpu": "2",
                        "memory": "1017552Ki",
                        "pods": "100"
                      },
                      "conditions": [
                        {
                          "type": "Ready",
                          "status": "True",
                          "lastHeartbeatTime": "2015-08-14T18:29:02Z",
                          "lastTransitionTime": "2015-08-14T18:27:18Z",
                          "reason": "kubelet is posting ready status"
                        }
                      ],
                      "addresses": [
                        {
                          "type": "LegacyHostIP",
                          "address": "10.245.1.5"
                        }
                      ],
                      "nodeInfo": {
                        "machineID": "6c30ad3b41fa4d1e89745cef6f22c4de",
                        "systemUUID": "70722494-C3F8-4AA6-9097-77C916F442FE",
                        "bootID": "d728ce2a-780c-40f9-b6e2-603b0dd195e2",
                        "kernelVersion": "3.17.4-301.fc21.x86_64",
                        "osImage": "Fedora 21 (Twenty One)",
                        "containerRuntimeVersion": "docker://1.6.2.fc21",
                        "kubeletVersion": "v0.19.3",
                        "kubeProxyVersion": "v0.19.3"
                      }
                    }
                  }
                ]
              }
          """
          val myNodes= Json.parse(nodesJsonStr).as[NodeList]
          myNodes.kind mustEqual "NodeList"
          myNodes.items.length mustEqual 3 // 3 minion nodes
          
          myNodes(0).name mustEqual "10.245.1.3"
          myNodes(0).status.get.capacity("memory") mustEqual Resource.Quantity("1017552Ki")
       
          myNodes(1).name mustEqual "10.245.1.4"
          myNodes(1).status.get.capacity("cpu") mustEqual Resource.Quantity("2")
         
          val readNodes = Json.fromJson[NodeList](Json.toJson(myNodes)).get 
          myNodes mustEqual readNodes
     }
  }

  "a Kubernetes v1.8 minikube node can be read and written as json " >> {

    val nodeJsonSource=Source.fromURL(getClass.getResource("/exampleNode.json"))
    val nodeJsonStr = nodeJsonSource.mkString
    val node = Json.parse(nodeJsonStr).as[Node]

    node.status.get.allocatable("cpu") mustEqual Resource.Quantity("2")
    node.status.get.allocatable("pods") mustEqual Resource.Quantity("110")

    node.spec.get.taints.size mustEqual 1
    node.spec.get.taints(0).effect mustEqual "NoSchedule"

    // write and read it back in again and compare
    val json = Json.toJson(node)
    val readNode = Json.fromJson[Node](json).get
    readNode mustEqual node
  }
}