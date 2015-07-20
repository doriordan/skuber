package skuber.api

import org.specs2.mutable.Specification // for unit-style testing
import org.specs2.execute.Result

import scala.math.BigInt

import skuber.model._
import skuber.model.Model._
import JsonReadWrite._

import play.api.libs.json._



/**
 * @author David O'Riordan
 */
class JsonReadsWritesSpec extends Specification {
  "This is a unit specification for the skuber json readers and writers.\n ".txt
  
  
  // Namespace reader and writer
  "A Namespace can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for the default namespace" >> {
      val ns = Json.fromJson[Namespace](Json.toJson(Namespace.default)).get
      ns.name mustEqual "default"
      ns mustEqual Namespace.default
    }
    "this can be done for a simple non-default namespace" >> {
      val myNs = Namespace.forName("myNamespace")
      val readNs = Json.fromJson[Namespace](Json.toJson(myNs)).get 
      myNs mustEqual readNs     
    }
    "this can be done for a more complex non-default namespace witha Spec and Status" >> {
      val f=List("myFinalizer", "Kubernetes")
      val phase="Running"
      
      val myOtherNs = Namespace.forName("myOtherNamespace").withFinalizers(f).withStatusOfPhase(phase)
      val readNs = Json.fromJson[Namespace](Json.toJson(myOtherNs)).get
      readNs mustEqual myOtherNs
    }
  }  
  
}