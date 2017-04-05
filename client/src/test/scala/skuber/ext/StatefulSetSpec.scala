package skuber.ext

import org.specs2.mutable.Specification // for unit-style testing

import skuber._
import skuber.LabelSelector.dsl._

import skuber.json.ext.format._

import play.api.libs.json._

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
      .withTemplate(template)
    stateSet.spec.get.template mustEqual Some(template)
    stateSet.spec.get.replicas mustEqual 200
    stateSet.name mustEqual "example"
    stateSet.status mustEqual None
  }


  "A StatefulSet object can be written to Json and then read back again successfully" >> {
    val container=Container(name="example",image="example")
    val template=Pod.Template.Spec.named("example").addContainer(container)
    val stateSet=StatefulSet("example")
      .withTemplate(template)
      .withLabelSelector(LabelSelector("live" doesNotExist, "microservice", "tier" is "cache", "env" isNotIn List("dev", "test")))


    val readDepl = Json.fromJson[StatefulSet](Json.toJson(stateSet)).get
    readDepl mustEqual stateSet
  }

  "A Deployment object can be read directly from a JSON string" >> {
    val deplJsonStr = """
{
  "apiVersion": "extensions/v1beta1",
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
    val stateSet = Json.parse(deplJsonStr).as[StatefulSet]
    stateSet.kind mustEqual "StatefulSet"
    stateSet.name mustEqual "nginx-stateset"
    stateSet.spec.get.replicas mustEqual 3
    stateSet.spec.get.template.get.metadata.labels mustEqual Map("app" -> "nginx")
    stateSet.spec.get.template.get.spec.get.containers.length mustEqual 1
    stateSet.spec.get.selector.get.requirements.size mustEqual 4
    stateSet.spec.get.selector.get.requirements.find(r => (r.key == "env")) mustEqual Some("env" isNotIn List("dev"))
    stateSet.spec.get.selector.get.requirements.find(r => (r.key == "domain")) mustEqual Some("domain" is "www.example.com")
  }
}
