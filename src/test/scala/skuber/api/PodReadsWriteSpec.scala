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
    "this can be done for a Pod with a more complex spec" >> {
      val cntrs=List(Container("myContainer", "myImage"),
                     Container(name="myContainer2", 
                               image = "myImage2", 
                               command=List("bash","ls"),
                               workingDir="/home/skuber",
                               ports=List(Container.Port(3234), Container.Port(3256,name="svc", hostIP="10.101.35.56")),
                               env=List(EnvVar("HOME", "/home/skuber")),
                               resources=Some(Resource.Requirements(limits=Map("cpu" -> "0.1"))),  
                               volumeMounts=List(Volume.Mount("mnt1","/mt1"), 
                                                 Volume.Mount("mnt2","/mt2", readOnly = true)),
                               readinessProbe=Some(Probe(HTTPGetAction(new URL("http://10.145.15.67:8100/ping")))),
                               lifeCycle=Some(Lifecycle(preStop=Some(ExecAction(List("/bin/bash", "probe"))))),
                               securityContext=Some(Security.Context(capabilities=Some(Security.Capabilities(add=List("CAP_KILL"),drop=List("CAP_AUDIT_WRITE")))))
                              )
                     )
      val vols = List(Volume("myVol1", Volume.Glusterfs("myEndpointsName", "/usr/mypath")),
                      Volume("myVol2", Volume.ISCSI("127.0.0.1:3260", "iqn.2014-12.world.server:www.server.world")))
      val pdSpec=Pod.Spec(containers=cntrs,
                          volumes=vols,
                          dnsPolicy=DNSPolicy.ClusterFirst,
                          nodeSelector=Map("diskType" -> "ssd", "machineSize" -> "large"),
                          imagePullSecrets=List(LocalObjectReference("abc"),LocalObjectReference("def"))
                         )
      val myPod = Namespace.forName("myNamespace").pod("myPod",Some(pdSpec))
                            
      val writtenPod = Json.toJson(myPod)
      val strs=Json.stringify(writtenPod)
      System.err.println(strs)    
      val readPodJsResult = Json.fromJson[Pod](writtenPod)
     
      val ret: Result = readPodJsResult match {
        case JsError(e) => Failure(e.toString)    
        case JsSuccess(readPod,_) => 
          readPod mustEqual myPod
      }   
      ret
    }
  }    
}