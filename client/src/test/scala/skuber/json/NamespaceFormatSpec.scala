package skuber.json

import org.specs2.mutable.Specification // for unit-style testing
import org.specs2.execute.Result
import org.specs2.execute.Failure
import org.specs2.execute.Success

import scala.math.BigInt

import java.util.Calendar

import skuber._
import format._

import play.api.libs.json._


/**
 * @author David O'Riordan
 */
class NamespaceFormatSpec extends Specification {
  "This is a unit specification for the skuber formatter for k8s namespaces.\n ".txt
  
  
  // Namespace reader and writer
  "A Namespace can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for the default namespace" >> {
      val ns = Json.fromJson[Namespace](Json.toJson(Namespace.default)).get
      ns.metadata.name mustEqual "default"
      ns mustEqual Namespace.default
    }
    "this can be done for a simple non-default namespace" >> {
      val myNs = Namespace.forName("myNamespace")
      val readNs = Json.fromJson[Namespace](Json.toJson(myNs)).get 
      myNs mustEqual readNs     
    }
    "this can be done for a more complex non-default namespace with a Spec and Status" >> {
      val f=List("myFinalizer", "Kubernetes")
      val phase="Running"
      
      val myOtherNs = Namespace.forName("myOtherNamespace").withFinalizers(f).withStatusOfPhase(phase)
      val readNs = Json.fromJson[Namespace](Json.toJson(myOtherNs)).get
      readNs mustEqual myOtherNs
    }

    "namespace spec allows finalizers to be optional" >> {
      val readSpec = Json.fromJson[Namespace.Spec](Json.parse("{}")).get
      readSpec.finalizers.isEmpty mustEqual true

      val readSpec2 = Json.fromJson[Namespace.Spec](Json.parse("""{ "finalizers": ["kubernetes"]}""")).get
      readSpec2.finalizers.get.head mustEqual "kubernetes"
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
       val res = Json.fromJson[Namespace](nsJson)
       val ret: Result = res match {
          case JsSuccess(ns,path) =>
            ns.name mustEqual "mynamespace"
            ns.apiVersion mustEqual "v1"
            ns.kind mustEqual "Namespace"
            ns.metadata.uid mustEqual "2a08e586-2d2d-11e5-99f8-0800279dd272"
            ns.status mustEqual Some(skuber.Namespace.Status("Active"))
            val date = ns.metadata.creationTimestamp.get
            date.getYear mustEqual 2015
            date.getMonth mustEqual java.time.Month.JULY
            date.getDayOfMonth mustEqual 18
            date.getHour mustEqual 9
            date.getMinute mustEqual 12
            date.getSecond mustEqual 50
            date.getOffset mustEqual java.time.ZoneOffset.UTC
            val date2 = ns.metadata.deletionTimestamp.get
            date2.getOffset mustEqual java.time.ZoneOffset.ofHours(1) 
            val labels=ns.metadata.labels
            labels("three") mustEqual "four"
            val annots=ns.metadata.annotations
            annots("abc") mustEqual "def"
            val res2 = Json.fromJson[Namespace](Json.toJson(ns)(namespaceFormat))(namespaceFormat)
            res2 match {
              case JsSuccess(ns2, path) => ns2.metadata.deletionTimestamp.get mustEqual ns.metadata.deletionTimestamp.get
              case JsError(e) => Failure(e.toString)
            }
          case JsError(e) => Failure(e.toString)
        }
        ret
    }    
  }    
}