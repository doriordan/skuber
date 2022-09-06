package skuber.ext

import org.specs2.mutable.Specification
import play.api.libs.json.Json
import skuber.{ObjectMeta, Scale}

/**
 * @author David O'Riordan
 */
class ScaleSpec extends Specification {
  "This is a unit specification for the skuber Scale class. ".txt
  
  "A Scale object can be constructed from a name and replica count" >> {
    val scale=Scale.named("example").withSpecReplicas(10)
    scale.spec.replicas mustEqual Some(10)
    scale.name mustEqual "example"
    scale.status mustEqual None
  }
  
  "A scale object can be written to Json and then read back again successfully" >> {

      val scale= Scale(apiVersion="autoscaling/v1",
        metadata=ObjectMeta(name="example", namespace="na"),
        spec=Scale.Spec(replicas=Some(10)),
        status = Some(Scale.Status(replicas = 10, selector = Some("env=prod,app=app1"))))
      
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
    "targetSelector": "redis-master",
    "selector": "app=service1,environment=dev"
  }
}
"""
    val scale = Json.parse(scaleJsonStr).as[Scale]
    scale.kind mustEqual "Scale"
    scale.name mustEqual "redis-master"
    scale.spec.replicas must beSome(1)
    scale.status must beSome(Scale.Status(replicas=1, selector=Some("app=service1,environment=dev"), targetSelector=Some("redis-master")))
    scale.statusSelectorLabels mustEqual Map("app" -> "service1", "environment" -> "dev")
  }

  "A scale object can contain NO replicas" >> {
    val scaleJsonObj =
      """
        |{
        |  "kind": "Scale",
        |  "apiVersion": "extensions/v1beta1",
        |  "metadata": {
        |    "name": "redis-master",
        |    "namespace": "default",
        |    "selfLink": "/apis/extensions/v1beta1/namespaces/default/replicationcontrollers/redis-master/scale",
        |    "creationTimestamp": "2015-12-29T11:55:14Z"
        |  },
        |  "spec": {
        |  },
        |  "status": {
        |    "replicas": 1,
        |    "targetSelector": "redis-master"
        |  }
        |}
      """.stripMargin
    val scale = Json.parse(scaleJsonObj).as[Scale]
    scale.kind mustEqual "Scale"
    scale.name mustEqual "redis-master"
    scale.spec.replicas mustEqual None
  }

  "A scale json with single key->value in status selector" >> {
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
    "targetSelector": "redis-master",
    "selector": "app=service1"
  }
}
"""
    val scale = Json.parse(scaleJsonStr).as[Scale]
    scale.kind mustEqual "Scale"
    scale.name mustEqual "redis-master"
    scale.spec.replicas must beSome(1)
    scale.status must beSome(Scale.Status(replicas=1, selector=Some("app=service1"), targetSelector=Some("redis-master")))
    scale.statusSelectorLabels mustEqual Map("app" -> "service1")
  }

  "A scale json with wrong spec status string should not produce errors" >> {
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
    "targetSelector": "redis-master",
    "selector": "app==se,,rvice1,==environment=dev,,"
  }
}
"""
    val scale = Json.parse(scaleJsonStr).as[Scale]
    scale.kind mustEqual "Scale"
    scale.name mustEqual "redis-master"
    scale.spec.replicas must beSome(1)
    scale.status must beSome(Scale.Status(replicas=1, selector=Some("app==se,,rvice1,==environment=dev,,"), targetSelector=Some("redis-master")))
  }
}