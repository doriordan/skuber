package skuber.apps

import org.specs2.mutable.Specification
import play.api.libs.json.Json
import skuber.LabelSelector.{IsEqualRequirement, NotExistsRequirement, NotInRequirement}
import skuber.LabelSelector.dsl._
import skuber._
import skuber.json.apps.format._
import scala.language.reflectiveCalls

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
    deployment.spec.get.template must beSome(template)
    deployment.spec.get.replicas must beSome(200)
    deployment.name mustEqual "example"
    deployment.status must beNone
  }
  
  
  "A Deployment object can be written to Json and then read back again successfully" >> {
      val container=Container(name="example",image="example")
      val template=Pod.Template.Spec.named("example").addContainer(container)

      val labelSelector = LabelSelector(NotExistsRequirement("live"), IsEqualRequirement("tier", "cache"),NotInRequirement("env", List("dev", "test")) )

      val deployment=Deployment("example")
        .withTemplate(template)
        .withLabelSelector(labelSelector)


      val readDepl = Json.fromJson[Deployment](Json.toJson(deployment)).get
      readDepl mustEqual deployment
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
            "resources": {
              "requests": {
                "cpu" : 1,
                "memory": "10Mi"
              }
            },
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

    val cpuResources: Resource.Quantity = depl.spec.get.template.get.spec.get.containers.head.resources.get.requests(Resource.cpu)
    val memoryResources: Resource.Quantity = depl.spec.get.template.get.spec.get.containers.head.resources.get.requests(Resource.memory)

    cpuResources mustEqual Resource.Quantity("1")
    memoryResources mustEqual Resource.Quantity("10Mi")

    depl.kind mustEqual "Deployment"
    depl.name mustEqual "nginx-deployment"
    depl.spec.get.replicas must beSome(3)
    depl.spec.get.template.get.metadata.labels mustEqual Map("app" -> "nginx")
    depl.spec.get.template.get.spec.get.containers.length mustEqual 1
    depl.spec.get.selector.get.requirements.size mustEqual 4
    depl.spec.get.selector.get.requirements.find(r => (r.key == "env")) must beSome("env" isNotIn List("dev"))
    depl.spec.get.selector.get.requirements.find(r => (r.key == "domain")) must beSome("domain" is "www.example.com")
  }
}