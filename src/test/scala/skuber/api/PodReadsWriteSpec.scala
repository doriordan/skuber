package skuber.api

import org.specs2.mutable.Specification // for unit-style testing
import org.specs2.execute.Result
import org.specs2.execute.Failure
import org.specs2.execute.Success

import scala.math.BigInt
import java.util.Calendar

import skuber.model._
import skuber.model.Model._
import JsonReadWrite._

import play.api.libs.json._



/**
 * @author David O'Riordan
 */
class PodReadsWritesSpec extends Specification {
  "This is a unit specification for the skuber Pod related json formatter.\n ".txt
  
  
  // Pod reader and writer
  "A Pod can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for a simple Pod with just a name" >> {
      val myPod = Pod.forName("myPod")
      val readPod = Json.fromJson[Pod](Json.toJson(myPod)).get 
      myPod mustEqual readPod    
    }
    "this can be done for a simple Pod with just a name and namespace set" >> {
      val myPod = Namespace.forName("myNamespace").pod("myPod")
      val readPod = Json.fromJson[Pod](Json.toJson(myPod)).get 
      myPod mustEqual readPod    
    } 
    "this can be done for a Pod with a simple, single container spec" >> {
      val myPod = Namespace.forName("myNamespace").
                    pod("myPod",
                        Some(Pod.Spec(
                            Container("myContainer", "myImage")::Nil)))
      val readPod = Json.fromJson[Pod](Json.toJson(myPod)).get 
      myPod mustEqual readPod
    }
  }    
}