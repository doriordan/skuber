package skuber.model.apps

import org.specs2.mutable.Specification
import play.api.libs.json.Json
import skuber.model.LabelSelector.dsl._
import skuber.model._
import skuber.json.apps.format._
import skuber.model.Pod

import scala.language.{postfixOps, reflectiveCalls}

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
    deployment.spec.get.template must beEqualTo(Some(template))
    deployment.spec.get.replicas must beEqualTo(Some(200))
    deployment.name must beEqualTo("example")
    deployment.status must beEqualTo(None)
  }
  
  
  "A Deployment object can be written to Json and then read back again successfully" >> {
      val container=Container(name="example",image="example")
      val template=Pod.Template.Spec.named("example").addContainer(container)
      val deployment=Deployment("example")
        .withTemplate(template)
          .withLabelSelector(LabelSelector("live" doesNotExist, "microservice", "tier" is "cache", "env" isNotIn List("dev", "test")))


      val readDepl = Json.fromJson[Deployment](Json.toJson(deployment)).get
      readDepl must beEqualTo(deployment)
  }

  "A Deployment object properly writes with zero replicas" >> {
    val deployment=Deployment("example").withReplicas(0)

    val writeDepl = Json.toJson(deployment)
    (writeDepl \ "spec" \ "replicas").asOpt[Int] must beSome(0)
  }

  "A Deployment object can be read directly from a JSON string" >> {
    val deplJsonStr = """
{
  "apiVersion": "apps/v1beta1",
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
    depl.kind must beEqualTo("Deployment")
    depl.name must beEqualTo("nginx-deployment")
    depl.spec.get.replicas must beEqualTo(Some(3))
    depl.spec.get.template.get.metadata.labels must beEqualTo(Map("app" -> "nginx"))
    depl.spec.get.template.get.spec.get.containers.length must beEqualTo(1)
    depl.spec.get.selector.get.requirements.size must beEqualTo(4)
    depl.spec.get.selector.get.requirements.find(r => (r.key == "env")) must beEqualTo(Some("env" isNotIn List("dev")))
    depl.spec.get.selector.get.requirements.find(r => (r.key == "domain")) must beEqualTo(Some("domain" is "www.example.com"))
  }
}