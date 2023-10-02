package skuber.examples.dynamicClient

import java.util.UUID.randomUUID
import org.apache.pekko.actor.ActorSystem
import play.api.libs.json.Json
import skuber.api.dynamic.client.impl.{DynamicKubernetesClientImpl, JsonRaw}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

object DynamicKubernetesClientImplExample extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

  private val deploymentName1: String = randomUUID().toString

  private val kubernetesDynamicClient = DynamicKubernetesClientImpl.build()

  private val createDeploymentInput = Json.parse {
    s"""
       {
        "apiVersion": "apps/v1",
        "kind": "Deployment",
        "metadata": {
          "name": "$deploymentName1"
        },
        "spec": {
          "replicas": 1,
          "selector": {
            "matchLabels": {
              "app": "nginx"
            }
          },
          "template": {
            "metadata": {
              "name": "nginx",
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
                      "containerPort": 80,
                      "protocol": "TCP"
                    }
                  ]
                }
              ]
            }
          }
        }
      }""".stripMargin
  }


  private val createdDeployment = kubernetesDynamicClient.create(JsonRaw(createDeploymentInput), resourcePlural = "deployments")

  val nameF = createdDeployment.flatMap { _ =>
    val getDeployment = kubernetesDynamicClient.get(
      deploymentName1,
      apiVersion = "apps/v1",
      resourcePlural = "deployments")
    getDeployment.map(_.metadata.map(_.name))
  }

  Await.result(nameF, 30.seconds)

  nameF.foreach { name =>
    println(s"Deployment name: $name")
  }

  kubernetesDynamicClient.delete(deploymentName1, apiVersion = "apps/v1", resourcePlural = "deployments")

  Await.result(system.terminate(), 10.seconds)

}
