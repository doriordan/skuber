package skuber.api

import org.specs2.mutable.Specification // for unit-style testing
import org.specs2.execute.Result
import org.specs2.execute.Failure
import org.specs2.execute.Success

import scala.math.BigInt
import java.util.Calendar
import java.net.URL

import skuber.model._
import skuber.model.Model._
import JsonReadWrite._

import play.api.libs.json._



/**
 * @author David O'Riordan
 */
class NodeReadsWritesSpec extends Specification {
  "This is a unit specification for the skuber Node related json formatter.\n ".txt
  
  import Node._
  
  // Node reader and writer
  "A Node can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for a simple Service with just a name" >> {
      val myNode = Node.forName("myNode")
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
    }
  }    
}