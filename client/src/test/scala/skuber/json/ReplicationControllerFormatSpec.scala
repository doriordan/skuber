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
class ReplicationControllerFormatSpec extends Specification {
  "This is a unit specification for the skuber Service related json formatter.\n ".txt
  
import ReplicationController._
  
  // RC reader and writer
  "A Replication Controller can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for a simple controller with just a name" >> {
      val myRC = ReplicationController("myRC")
      val readRC = Json.fromJson[ReplicationController](Json.toJson(myRC)).get 
      myRC mustEqual readRC   
    }
    
    "this can be done for a simple controller with just a name and namespace set" >> {
      val myRC = Namespace("myNamespace").replicationController("myRC")
      val readRC = Json.fromJson[ReplicationController](Json.toJson(myRC)).get 
      myRC mustEqual readRC  
    } 
    
    "this can be done for a controller with a spec for replicating a pod with a single container" >> {
      val templateSpec=Pod.Template.Spec(metadata=ObjectMeta(name="rc"),
                    spec=Some(Pod.Spec(List(Container(name="test", image="test")))))
                    
      val myRC = Namespace("myNamespace").replicationController("myRC").
                    withTemplate(templateSpec).
                    withReplicas(5).
                    withSelector("app" -> "test")
                    
                   
      val readRC = Json.fromJson[ReplicationController](Json.toJson(myRC)).get 
      myRC mustEqual readRC
    }
    
    "a replication controller can be read from Json" >> {
      val svcJsonStr="""
          {
           "kind":"ReplicationController",
           "apiVersion":"v1",
           "metadata":{
              "name":"guestbook",
              "labels":{
                 "app":"guestbook"
              }
           },
         "spec":{
            "replicas":3,
            "selector":{
               "app":"guestbook"
            },
            "template":{
               "metadata":{
                  "labels":{
                     "app":"guestbook"
                   }
               },
               "spec":{
                  "containers":[
                    {
                      "name":"guestbook",
                      "image":"kubernetes/guestbook:v2",
                      "ports":[
                         {
                          "name":"http-server",
                          "containerPort":3000
                         }
                      ]
                    }
                  ]
               }
            }
         }    
      }        
"""
      val myRC = Json.parse(svcJsonStr).as[ReplicationController]
      myRC.kind mustEqual "ReplicationController"
      myRC.name mustEqual "guestbook"
      val spec = myRC.spec.get
      spec.replicas mustEqual 3 
    }
    
    
  }    
}