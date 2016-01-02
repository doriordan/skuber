package skuber.ext

import org.specs2.mutable.Specification // for unit-style testing

import scala.math.BigInt

import skuber.{ReplicationController,Pod,Container, ObjectMeta}

import skuber.json.ext.format._

import play.api.libs.json._

/**
 * @author David O'Riordan
 */
class DeploymentSpec extends Specification {
  "This is a unit specification for the skuber Deployment class. ".txt
  
  "A Deployment object can be constructed from a name and pod template spec" >> {
    val container=Container(name="example",image="example")
    val podTemplateSpec=Pod.Template.Spec.named("example").addContainer(container)
    val deployment=Deployment(metadata = ObjectMeta(name="example"),
                              spec=Deployment.Spec(template=podTemplateSpec))
    deployment.spec.template mustEqual podTemplateSpec
    deployment.name mustEqual "example"
    deployment.status mustEqual None
  }
  
  
  "A Deployment object can be written to Json and then read back again successfully" >> {
      val container=Container(name="example",image="example")
      val podTemplateSpec=Pod.Template.Spec.named("example").addContainer(container)
      val deployment=Deployment(spec=Deployment.Spec(template=podTemplateSpec))
     
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
    depl.spec.replicas mustEqual 3
    depl.spec.template.metadata.labels mustEqual Map("app" -> "nginx")
    depl.spec.template.spec.get.containers.length mustEqual 1
  }
}