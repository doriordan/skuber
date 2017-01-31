package skuber.ext

import org.specs2.mutable.Specification // for unit-style testing

import scala.math.BigInt

import skuber._
import skuber.LabelSelector.dsl._

import skuber.json.ext.format._

import play.api.libs.json._

/**
 * @author David O'Riordan
 */
class DeploymentSpec extends Specification {
  "This is a unit specification for the skuber Deployment class. ".txt
  
  "A Deployment object can be constructed from a name and pod template spec" >> {
    val container=Container(name="example",image="example")
    val template=Pod.Template.Spec.named("example").addContainer(container)
    val deployment=Deployment("example")
      .withReplicas(200)
      .withTemplate(template)
    deployment.spec.get.template mustEqual Some(template)
    deployment.spec.get.replicas mustEqual 200
    deployment.name mustEqual "example"
    deployment.status mustEqual None
  }
  
  
  "A Deployment object can be written to Json and then read back again successfully" >> {
      val container=Container(name="example",image="example")
      val template=Pod.Template.Spec.named("example").addContainer(container)
      val deployment=Deployment("example")
        .withTemplate(template)
          .withLabelSelector(LabelSelector("live" doesNotExist, "microservice", "tier" is "cache", "env" isNotIn List("dev", "test")))


      val readDepl = Json.fromJson[Deployment](Json.toJson(deployment)).get
      readDepl mustEqual deployment
  }
  
  "A Deployment object can be read directly from a JSON string" >> {
    val deplJsonStr = """
{
  "apiVersion": "extensions/v1beta1",
  "kind": "Deployment",
  "metadata": {
    "name": "nginx-deployment"
  },
  "spec": {
    "replicas": 3,
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
    "strategy": {
      "rollingUpdate": {
        "maxUnavailable": 1
      }
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
    }
  }
}            
"""
    val depl = Json.parse(deplJsonStr).as[Deployment]
    depl.kind mustEqual "Deployment"
    depl.name mustEqual "nginx-deployment"
    depl.spec.get.replicas mustEqual 3
    depl.spec.get.template.get.metadata.labels mustEqual Map("app" -> "nginx")
    depl.spec.get.template.get.spec.get.containers.length mustEqual 1
    depl.spec.get.selector.get.requirements.size mustEqual 4
    depl.spec.get.selector.get.requirements.find(r => (r.key == "env")) mustEqual Some("env" isNotIn List("dev"))
    depl.spec.get.selector.get.requirements.find(r => (r.key == "domain")) mustEqual Some("domain" is "www.example.com")
  }
}