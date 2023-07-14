package skuber

import java.util.UUID.randomUUID
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.FutureUtil.FutureOps
import skuber.api.dynamic.client.impl.{DynamicKubernetesClientImpl, DynamicKubernetesObject, JsonRaw}
import skuber.apps.v1.Deployment
import scala.concurrent.Future

class DynamicKubernetesClientImplTest extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {

  val deploymentName1: String = randomUUID().toString

  private val kubernetesDynamicClient = DynamicKubernetesClientImpl.build()

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(deploymentName1).map { name =>
      k8s.delete[Deployment](name).withTimeout().recover { case _ => () }
    }).withTimeout()

    results.futureValue

  }

  behavior of "DynamicKubernetesClientImplTest"


  it should "create a deployment" in { _ =>

    val createdDeployment = Json.parse {
      s"""
        "apiVersion": "apps/v1",
       {
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


    val createdDeploymentResponse = kubernetesDynamicClient.create(JsonRaw(createdDeployment), resourcePlural = "deployments").valueT

    assert(createdDeploymentResponse.metadata.map(_.name).contains(deploymentName1))

    val getDeployment: DynamicKubernetesObject = kubernetesDynamicClient.get(
      deploymentName1,
      apiVersion = "apps/v1",
      resourcePlural = "deployments").valueT

    assert(getDeployment.metadata.map(_.name).contains(deploymentName1))
  }

}
