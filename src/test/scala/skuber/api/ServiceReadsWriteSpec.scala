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
class ServiceReadsWritesSpec extends Specification {
  "This is a unit specification for the skuber Service related json formatter.\n ".txt
  
  import Service._
  
  // Service reader and writer
  "A Service can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for a simple Service with just a name" >> {
      val mySvc = Service.forName("mySvc")
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
  }    
}