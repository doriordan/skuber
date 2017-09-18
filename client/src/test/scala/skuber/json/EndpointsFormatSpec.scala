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
class EndpointsFormatSpec extends Specification {
  "This is a unit specification for the skuber Endpoint related json formatter.\n ".txt
  
import  Endpoints._
  
  // Endpoint reader and writer
  "An Endpoints can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for a Endpoints with just a single IP address/port" >> {
      val mySvc = Service("myApp")
      val myEndpoints = mySvc.mapsToEndpoint("10.247.0.10", 6777)
      val readEndpts = Json.fromJson[Endpoints](Json.toJson(myEndpoints)).get 
      myEndpoints mustEqual readEndpts    
    }
    "this can be done for an Endpoints with a single subset containing two addresses" >> {
      val mySvc = Namespace("myNamespace").service("mySvc")
      val endptsSubset = Subset(Address("10.247.0.10")::Address("10.247.0.11")::Nil,Nil, Port(6777)::Nil)
      val mySvcEndpoints = mySvc.mapsToEndpoints(endptsSubset)
      
      val readSvcEndpoints = Json.fromJson[Endpoints](Json.toJson(mySvcEndpoints)).get 
      mySvcEndpoints mustEqual readSvcEndpoints 
    } 
    "this can be done for a more complex endppoints mapping" >> {
      val mySvc = Namespace("myNamespace").service("mySvc")
      val endptsSubs1 = Subset(Address("10.247.0.10")::Address("10.247.0.11")::Nil,Nil,Port(6777)::Nil)
      val endptsSubs2 = Subset(Address("10.213.0.1")::Nil, Nil, Port(6444, Protocol.TCP, "portTCP")::Port(6445, Protocol.UDP, "portUDP")::Nil)
      val endptsSubsets=endptsSubs1::endptsSubs2::Nil
      val mySvcEndpoints = mySvc.mapsToEndpoints(endptsSubsets)
      
      val readSvcEndpoints = Json.fromJson[Endpoints](Json.toJson(mySvcEndpoints)).get 
      mySvcEndpoints mustEqual readSvcEndpoints 
    }
    "an endpoints can be read from Json" >> {
      val epsJsonStr="""
        {
            "kind": "Endpoints",
            "apiVersion": "v1",
            "metadata": {
              "name": "kube-dns",
              "namespace": "default",
              "selfLink": "/api/v1/namespaces/default/endpoints/kube-dns",
              "uid": "0b9524f5-4509-11e5-bfa4-0800279dd272",
              "resourceVersion": "17261",
              "creationTimestamp": "2015-08-17T17:54:45Z",
              "labels": {
                "k8s-app": "kube-dns",
                "kubernetes.io/cluster-service": "true",
                "kubernetes.io/name": "KubeDNS"
              }
            },
            "subsets": [
              {
                "addresses": [
                  {
                    "ip": "10.246.1.3",
                    "targetRef": {
                      "kind": "Pod",
                      "namespace": "default",
                      "name": "kube-dns-v3-fkelw",
                      "uid": "14e2636e-4630-11e5-bfa4-0800279dd272",
                      "resourceVersion": "17260"
                    }
                  }
                ],
                "notReadyAddresses": [],
                "ports": [
                  {
                    "name": "dns-tcp",
                    "port": 53,
                    "protocol": "TCP"
                  },
                  {
                    "name": "dns",
                    "port": 53,
                    "protocol": "UDP"
                  }
                ]
              }
            ]
          } 
        """
     val endps = Json.parse(epsJsonStr).as[Endpoints]
     endps.kind mustEqual "Endpoints"
     endps.name mustEqual "kube-dns"
     endps.metadata.labels.size mustEqual 3
     endps.metadata.labels("kubernetes.io/name") mustEqual "KubeDNS"
     endps.subsets.size mustEqual 1
     endps.subsets(0).addresses.size mustEqual 1
     endps.subsets(0).addresses(0).ip mustEqual("10.246.1.3")
     val tgtRef = endps.subsets(0).addresses(0).targetRef.get
     tgtRef.kind mustEqual "Pod"
     tgtRef.name mustEqual "kube-dns-v3-fkelw"
     val ports = endps.subsets(0).ports
     ports.length mustEqual 2
     ports(0).name mustEqual "dns-tcp"
     ports(0).protocol mustEqual Protocol.TCP
     ports(0).port mustEqual 53
     ports(1).name mustEqual "dns"
     ports(1).protocol mustEqual Protocol.UDP
     ports(1).port mustEqual 53   
    }
  }    
}