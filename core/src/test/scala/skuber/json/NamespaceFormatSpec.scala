package skuber.json

import org.specs2.mutable.Specification // for unit-style testing
import play.api.libs.json._
import skuber.model.Namespace
import format.{namespaceFormat, nsSpecFormat}


/**
 * @author David O'Riordan
 */
class NamespaceFormatSpec extends Specification {
  "This is a unit specification for the skuber formatter for k8s namespaces.\n ".txt
  
  
  // Namespace reader and writer
  "A Namespace can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for the default namespace" >> {
      val ns = Json.fromJson[Namespace](Json.toJson(Namespace.default)).get
      ns.metadata.name must beEqualTo("default")
      ns must beEqualTo(Namespace.default)
    }
    "this can be done for a simple non-default namespace" >> {
      val myNs = Namespace.forName("myNamespace")
      val readNs = Json.fromJson[Namespace](Json.toJson(myNs)).get 
      myNs must beEqualTo(readNs)     
    }
    "this can be done for a more complex non-default namespace with a Spec and Status" >> {
      val f=List("myFinalizer", "Kubernetes")
      val phase="Running"
      
      val myOtherNs = Namespace.forName("myOtherNamespace").withFinalizers(f).withStatusOfPhase(phase)
      val readNs = Json.fromJson[Namespace](Json.toJson(myOtherNs)).get
      readNs must beEqualTo(myOtherNs)
    }

    "namespace spec allows finalizers to be optional" >> {
      val readSpec = Json.fromJson[Namespace.Spec](Json.parse("{}")).get
      readSpec.finalizers.isEmpty must beEqualTo(true)

      val readSpec2 = Json.fromJson[Namespace.Spec](Json.parse("""{ "finalizers": ["kubernetes"]}""")).get
      readSpec2.finalizers.get.head must beEqualTo("kubernetes")
    }

    "we can read a namespace from a direct JSON string" >> {
      val nsJson = Json.parse("""
        {
          "kind": "Namespace",
          "apiVersion": "v1",
          "metadata": {
            "name": "mynamespace",
            "selfLink": "/api/v1/namespaces/mynamespace",
            "uid": "2a08e586-2d2d-11e5-99f8-0800279dd272",
            "resourceVersion": "26101",
            "creationTimestamp": "2015-07-18T09:12:50Z",
            "deletionTimestamp": "2015-07-18T09:12:50+01:00",
            "labels": {
                "one" : "two",
                "three" : "four"
            },
            "annotations": {
                "abc": "def",
                "ghj": "brd"
            }
          },
          "spec": {
              "finalizers": [
                "kubernetes"
              ]
          },
          "status": {
             "phase": "Active"
          }
        }
      """)

      val parsedNs = Json.fromJson[Namespace](nsJson)
      val ns: Namespace = parsedNs.get
      ns.name must beEqualTo("mynamespace")
      ns.apiVersion must beEqualTo("v1")
      ns.kind must beEqualTo("Namespace")
      ns.metadata.uid must beEqualTo("2a08e586-2d2d-11e5-99f8-0800279dd272")
      ns.status must beEqualTo(Some(Namespace.Status("Active")))
      val date = ns.metadata.creationTimestamp.get
      date.getYear must beEqualTo(2015)
      date.getMonth must beEqualTo(java.time.Month.JULY)
      date.getDayOfMonth must beEqualTo(18)
      date.getHour must beEqualTo(9)
      date.getMinute must beEqualTo(12)
      date.getSecond must beEqualTo(50)
      date.getOffset must beEqualTo(java.time.ZoneOffset.UTC)
      val date2 = ns.metadata.deletionTimestamp.get
      date2.getOffset must beEqualTo(java.time.ZoneOffset.ofHours(1))
      val labels=ns.metadata.labels
      labels("three") must beEqualTo("four")
      val annots=ns.metadata.annotations
      annots("abc") must beEqualTo("def")
      val ns2Json = Json.toJson(ns)
      val res2 = Json.fromJson[Namespace](ns2Json)
      res2.get.metadata.deletionTimestamp.get must beEqualTo(ns.metadata.deletionTimestamp.get)
    }
  }
}