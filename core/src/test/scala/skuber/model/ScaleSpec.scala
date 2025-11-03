package skuber.model

import org.specs2.mutable.Specification
import play.api.libs.json._

/**
 * @author David O'Riordan
 */
class ScaleSpec extends Specification {
  "This is a unit specification for the skuber Scale class. ".txt
  
  "A Scale object can be constructed from a name and replica count" >> {
    val scale=Scale.named("example").withSpecReplicas(10)
    scale.spec.replicas must beEqualTo(Some(10))
    scale.name must beEqualTo("example")
    scale.status must beEqualTo(None)
  }
  
  "A scale object can be written to Json and then read back again successfully" >> {

      val scale= Scale(
        apiVersion="autoscaling/v1",
        metadata=ObjectMeta(name="example", namespace="na"),
        spec=Scale.Spec(replicas=Some(10))
      )
      
      val readScale = Json.fromJson[Scale](Json.toJson(scale)).get
      readScale must beEqualTo(scale)
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
    "targetSelector": "redis-master"
  }
}
"""
    val scale = Json.parse(scaleJsonStr).as[Scale]
    scale.kind must beEqualTo("Scale")
    scale.name must beEqualTo("redis-master")
    scale.spec.replicas must beEqualTo(Some(1))
    scale.status must beEqualTo(Some(Scale.Status(replicas=1, selector=None, targetSelector=Some("redis-master"))))
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
    scale.kind must beEqualTo("Scale")
    scale.name must beEqualTo("redis-master")
    scale.spec.replicas must beEqualTo(None)
  }
}