package skuber.apps

import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.LabelSelector.dsl._
import skuber._
import skuber.json.apps.format._

/**
  * Created by hollinwilkins on 4/5/17.
  */
class StatefulSetSpec extends Specification {
  "This is a unit specification for the skuber StatefulSet class. ".txt

  "A StatefulSet object can be constructed from a name and pod template spec" >> {
    val container=Container(name="example",image="example")
    val template=Pod.Template.Spec.named("example").addContainer(container)
    val stateSet=StatefulSet("example")
      .withReplicas(200)
      .withServiceName("nginx-service")
      .withTemplate(template)
      .withVolumeClaimTemplate(PersistentVolumeClaim("hello"))
    stateSet.spec.get.template mustEqual template
    stateSet.spec.get.serviceName mustEqual Some("nginx-service")
    stateSet.spec.get.replicas must beSome(200)
    stateSet.spec.get.volumeClaimTemplates.size mustEqual 1
    stateSet.name mustEqual "example"
    stateSet.status mustEqual None
  }


  "A StatefulSet object can be written to Json and then read back again successfully" >> {
    val container=Container(name="example",image="example")
    val template=Pod.Template.Spec.named("example").addContainer(container)
    val stateSet=StatefulSet("example")
      .withTemplate(template)
      .withLabelSelector(LabelSelector("live" doesNotExist, "microservice", "tier" is "cache", "env" isNotIn List("dev", "test")))


    val readSSet = Json.fromJson[StatefulSet](Json.toJson(stateSet)).get
    readSSet mustEqual stateSet
  }

  "A StatefulSet object properly writes with zero replicas" >> {
    val sset=StatefulSet("example").withReplicas(0)

    val writeSSet = Json.toJson(sset)
    (writeSSet \ "spec" \ "replicas").asOpt[Int] must beSome(0)
  }

  "A StatefulSet object can be read directly from a JSON string" >> {
    val ssetJsonStr = """
{
  "apiVersion": "apps/v1beta1",
  "kind": "StatefulSet",
  "metadata": {
    "name": "nginx-stateset"
  },
  "spec": {
    "replicas": 3,
    "serviceName": "nginx-service",
    "selector": {
      "matchLabels" : {
        "domain": "www.example.com",
        "proxies": "microservices"
      },
      "matchExpressions": [
        {"key": "env", "operator": "NotIn", "values": ["dev"]},
        {"key": "tier","operator": "In", "values": ["frontend"]}
      ]
    },
    "template": {
      "metadata": {
        "labels": {
          "app": "nginx"
        }
      },
      "spec": {
        "containers": [
          {
            "name": "nginx",
            "image": "nginx:1.7.9",
            "ports": [
              {
                "containerPort": 80
              }
            ]
          }
        ]
      }
    },
    "volumeClaimTemplates": [
      {
        "metadata": { "name": "nginx" },
        "spec": {
          "accessModes": ["ReadWriteOnce"],
          "resources": {
            "requests": {
              "capacity": "1Gi"
            }
          }
        }
      }
    ]
  }
}
"""
    val stateSet = Json.parse(ssetJsonStr).as[StatefulSet]
    stateSet.kind mustEqual "StatefulSet"
    stateSet.name mustEqual "nginx-stateset"
    stateSet.spec.get.replicas must beSome(3)
    stateSet.spec.get.volumeClaimTemplates.size mustEqual 1
    stateSet.spec.get.serviceName.get mustEqual "nginx-service"
    stateSet.spec.get.template.metadata.labels mustEqual Map("app" -> "nginx")
    stateSet.spec.get.template.spec.get.containers.length mustEqual 1
    stateSet.spec.get.selector.get.requirements.size mustEqual 4
    stateSet.spec.get.selector.get.requirements.find(r => (r.key == "env")) mustEqual Some("env" isNotIn List("dev"))
    stateSet.spec.get.selector.get.requirements.find(r => (r.key == "domain")) mustEqual Some("domain" is "www.example.com")
  }
}
