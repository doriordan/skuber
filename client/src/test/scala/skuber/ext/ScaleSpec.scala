package skuber.ext

import org.specs2.mutable.Specification // for unit-style testing

import scala.math.BigInt

import skuber.{ReplicationController,Pod,Container}
import skuber.json.ext.format._

import play.api.libs.json._

/**
 * @author David O'Riordan
 */
class ScaleSpec extends Specification {
  "This is a unit specification for the skuber Scale class. ".txt
  
  "A Scale object can be constructed from a name and replica count" >> {
    val scale=Scale.named("example").withReplicas(10)
    scale.spec.replicas mustEqual 10
    scale.name mustEqual "example"
    scale.status mustEqual None
  }
  
  "A scale object can be conrtucted for a given replication controller" >> {
      val container=Container(name="example",image="example")
      val podSpec=Pod.Spec(containers=List(container))
      val rc=ReplicationController("myRC").withPodSpec(podSpec)
      val scale=Scale.scale(rc).withReplicas(20)
      
      scale.spec.replicas mustEqual 20
  }
   
  "A scale object can be conrtucted for a given Deployment" >> {
      val container=Container(name="example",image="example")
      val podTemplateSpec=Pod.Template.Spec.named("example").addContainer(container)
      val deployment=Deployment(spec=Deployment.Spec(template=podTemplateSpec))
      val scale=Scale.scale(deployment).withReplicas(20)
      
      scale.spec.replicas mustEqual 20
  }
  
  "A scale object can be written to Json and then read back again successfully" >> {
      val container=Container(name="example",image="example")
      val podTemplateSpec=Pod.Template.Spec.named("example").addContainer(container)
      val deployment=Deployment(spec=Deployment.Spec(template=podTemplateSpec))
      val scale=Scale.scale(deployment).withReplicas(20)
      
      val readScale = Json.fromJson[Scale](Json.toJson(scale)).get
      readScale mustEqual scale
  }
  
  "A scale object can be read directly from a JSON string" >> {
    val scaleJsonStr = """
{
  "kind": "Scale",
  "apiVersion": "extensions/v1beta1",
  "metadata": {
    "name": "redis-master",
    "namespace": "default",
    "selfLink": "/apis/extensions/v1beta1/namespaces/default/replicationcontrollers/redis-master/scale",
    "creationTimestamp": "2015-12-29T11:55:14Z"
  },
  "spec": {
    "replicas": 1
  },
  "status": {
    "replicas": 1,
    "selector": {
      "name": "redis-master"
    }
  }
}
"""
    val scale = Json.parse(scaleJsonStr).as[Scale]
    scale.kind mustEqual "Scale"
    scale.name mustEqual "redis-master"
    scale.spec.replicas mustEqual 1
      scale.status mustEqual Some(Scale.Status(replicas=1,
                                               selector=Map("name" -> "redis-master")))
  }
 
}