package skuber.json

import org.specs2.mutable.Specification
import org.specs2.execute.Result
import org.specs2.execute.Failure

import skuber.model._
import format._
import play.api.libs.json._
import skuber.model.Service



/**
 * @author David O'Riordan
 */
class ServiceReadsWritesSpec extends Specification {
  "This is a unit specification for the skuber Service related json formatter.\n ".txt
  
  import skuber.model.Service._
  
  // Service reader and writer
  "A Service can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for a simple Service with just a name" >> {
      val mySvc = Service("mySvc")
      val readSvc = Json.fromJson[Service](Json.toJson(mySvc)).get 
      mySvc must beEqualTo(readSvc)    
    }
    "this can be done for a simple Service with just a name and namespace set" >> {
      val mySvc = Namespace("myNamespace").service("mySvc")
      val readSvc = Json.fromJson[Service](Json.toJson(mySvc)).get 
      mySvc must beEqualTo(readSvc)  
    } 
    "this can be done for a Service with a simple, single port spec" >> {
      val mySvc = Namespace("myNamespace").
                    service("mySvc",Spec(ports=List(Port(name="myPort",port=5654))))
      val readSvc = Json.fromJson[Service](Json.toJson(mySvc)).get 
      mySvc must beEqualTo(readSvc)
      val spec = readSvc.spec.get
      spec.publishNotReadyAddresses must beEqualTo(false)
    }
    "this can be done for a Service with a more complex spec" >> {
      val ports=List(Port(port=9081,targetPort=Some(8080)),
                     Port(name="xmit",protocol=Protocol.UDP, port=9563, nodePort=4561))
      val selector=Map("env" -> "production", "svc" -> "authorise")
      val mySvc=Namespace.default.service("mySvc", Spec(ports, selector, clusterIP="None", sessionAffinity=Affinity.ClientIP))
      
     
      val writtenSvc = Json.toJson(mySvc)
      val readSvcJsResult = Json.fromJson[Service](writtenSvc)
     
      val ret: Result = readSvcJsResult match {
        case JsError(e) => Failure(e.toString)    
        case JsSuccess(readSvc,_) => 
          readSvc must beEqualTo(mySvc)
      }   
      ret
    }
    "a service can be read from Json" >> {
      val svcJsonStr="""
          {
              "kind": "Service",
              "apiVersion": "v1",
              "metadata": {
                "name": "kube-dns",
                "namespace": "default",
                "selfLink": "/api/v1/namespaces/default/services/kube-dns",
                "uid": "8261aa82-4239-11e5-9586-0800279dd272",
                "resourceVersion": "21",
                "creationTimestamp": "2015-08-14T04:04:07Z",
                "labels": {
                  "k8s-app": "kube-dns",
                  "kubernetes.io/cluster-service": "true",
                  "kubernetes.io/name": "KubeDNS"
                }
              },
              "spec": {
                "ports": [
                  {
                    "name": "dns",
                    "protocol": "UDP",
                    "port": 53,
                    "targetPort": 53,
                    "nodePort": 0
                  },
                  {
                    "name": "dns-tcp",
                    "protocol": "TCP",
                    "port": 53,
                    "targetPort": 53,
                    "nodePort": 0
                  }
                ],
                "selector": {
                  "k8s-app": "kube-dns"
                },
                "clusterIP": "10.247.0.10",
                "type": "ClusterIP",
                "sessionAffinity": "None",
                "publishNotReadyAddresses": true
              },
              "status": {
                "loadBalancer": {}
              }
            }                   
      """
      val mySvc = Json.parse(svcJsonStr).as[Service]
      mySvc.kind must beEqualTo("Service")
      mySvc.name must beEqualTo("kube-dns")
      val spec = mySvc.spec.get
      spec.externalTrafficPolicy must beEqualTo(None)
      spec.publishNotReadyAddresses must beEqualTo(true)
      val ports = spec.ports
      ports.length must beEqualTo(2)
      val udpDnsPort = ports.head
      udpDnsPort.port must beEqualTo(53)
      udpDnsPort.targetPort.get must beEqualTo(Left(53))
      udpDnsPort.nodePort must beEqualTo(0)
      udpDnsPort.protocol must beEqualTo(Protocol.UDP)
      udpDnsPort.name must beEqualTo("dns")
      
      val tcpDnsPort = ports(1)
      tcpDnsPort.port must beEqualTo(53)
      tcpDnsPort.targetPort.get must beEqualTo(Left(53))
      tcpDnsPort.nodePort must beEqualTo(0)
      tcpDnsPort.protocol must beEqualTo(Protocol.TCP)
      tcpDnsPort.name must beEqualTo("dns-tcp")     
    }
  }    
}
