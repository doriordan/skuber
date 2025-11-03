package skuber.model

import org.specs2.mutable.Specification

/**
 * @author David O'Riordan
 */
class ModelSpec extends Specification {
  "This is a unit specification for the skuber data model. ".txt
  
  
  // Pod(s)
  "A Pods type can be constructed from lists of Pods\n" >> {
    "where an empty list of pods can be used" >> {
      val podList = List[Pod]()
      val pods=PodList(items = podList)
      pods.items.size must beEqualTo(0)
    }
    
    "where a list with a single pod can be used" >> {
      val container1=Container(name="Container1","image1")
      val container2=Container(name="Container2","image2")
      val podspec=Pod.Spec(volumes=Nil, containers=List(container1, container2))
      val pod=Pod("MyPod", podspec)
      val podList = List[Pod](pod)
      val pods=PodList(items = podList)
      pods.items.size must beEqualTo(1)
      pods.items.head.metadata.name must beEqualTo("MyPod")
    }    
  }   
   // ReplicationController(s)
  "A ReplicationControllers type can be constructed from a list of ReplicationController (a.k.a. RC)\n" >> {
    "where an empty list of RCs can be used" >> {
      val rcList = List[ReplicationController]()
      val rcs=ReplicationControllerList(items = rcList)
      rcs.items.size must beEqualTo(0)
    }
    
    "where a list with a single RC can be used" >> {
      val container1=Container("Container1","image1")
      val container2=Container(name="Container2", "image2")
      val podspec=Pod.Spec(containers=List(container1, container2))
      val rc = ReplicationController("MyRepCon").withReplicas(2).withPodSpec(podspec)
      val rcList = List[ReplicationController](rc)
      val rcs=ReplicationControllerList(items = rcList)
      rcs.items.size must beEqualTo(1)
      rcs.items.head.metadata.name must beEqualTo("MyRepCon")
      rcs(0).name must beEqualTo("MyRepCon")
      rcs(0).spec.get.replicas must beEqualTo(2)
      val pspec = for (
          rcspec <- rcs(0).spec;
          tmpl <- rcspec.template;
          podspec <- tmpl.spec
      ) yield(podspec)
      pspec.get.containers.size must beEqualTo(2)
    }    
  }   
}