package skuber.json

import org.specs2.mutable.Specification // for unit-style testing
import org.specs2.execute.Result
import org.specs2.execute.Failure
import org.specs2.execute.Success

import scala.math.BigInt

import java.util.Calendar
import java.net.URL

import skuber._
import format._

import play.api.libs.json._



/**
 * @author David O'Riordan
 */
class ServiceReadsWritesSpec extends Specification {
  "This is a unit specification for the skuber Service related json formatter.\n ".txt
  
import Service._
  
  // Service reader and writer
  "A Service can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for a simple Service with just a name" >> {
      val mySvc = Service("mySvc")
      val readSvc = Json.fromJson[Service](Json.toJson(mySvc)).get 
      mySvc mustEqual readSvc    
    }
    "this can be done for a simple Service with just a name and namespace set" >> {
      val mySvc = Namespace("myNamespace").service("mySvc")
      val readSvc = Json.fromJson[Service](Json.toJson(mySvc)).get 
      mySvc mustEqual readSvc  
    } 
    "this can be done for a Service with a simple, single port spec" >> {
      val mySvc = Namespace("myNamespace").
                    service("mySvc",Spec(ports=List(Port(name="myPort",port=5654))))
      val readSvc = Json.fromJson[Service](Json.toJson(mySvc)).get 
      mySvc mustEqual readSvc
    }
    "this can be done for a Service with a more complex spec" >> {
      val ports=List(Port(port=9081,targetPort=Some(8080)),
                     Port(name="xmit",protocol=Protocol.UDP, port=9563, nodePort=4561))
      val selector=Map("env" -> "production", "svc" -> "authorise")
      val mySvc=Namespace.default.service("mySvc", Spec(ports, selector, clusterIP="None", sessionAffinity=Affinity.ClientIP))
      
     
      val writtenSvc = Json.toJson(mySvc)
      val strs=Json.stringify(writtenSvc)
      // System.err.println(strs)    
      val readSvcJsResult = Json.fromJson[Service](writtenSvc)
     
      val ret: Result = readSvcJsResult match {
        case JsError(e) => Failure(e.toString)    
        case JsSuccess(readSvc,_) => 
          readSvc mustEqual mySvc
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
      mySvc.kind mustEqual "Service"
      mySvc.name mustEqual "kube-dns"
      val spec = mySvc.spec.get
      spec.externalTrafficPolicy mustEqual None
      spec.publishNotReadyAddresses mustEqual true
      val ports = spec.ports
      ports.length mustEqual 2
      val udpDnsPort = ports(0)
      udpDnsPort.port mustEqual 53
      udpDnsPort.targetPort.get.left.get mustEqual 53
      udpDnsPort.nodePort mustEqual 0
      udpDnsPort.protocol mustEqual Protocol.UDP
      udpDnsPort.name mustEqual "dns"
      
      val tcpDnsPort = ports(1)
      tcpDnsPort.port mustEqual 53
      tcpDnsPort.targetPort.get.left.get mustEqual 53
      tcpDnsPort.nodePort mustEqual 0
      tcpDnsPort.protocol mustEqual Protocol.TCP
      tcpDnsPort.name mustEqual "dns-tcp"     
    }
  }    
}
