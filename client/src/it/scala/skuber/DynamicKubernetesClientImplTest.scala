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
import scala.concurrent.duration._

class DynamicKubernetesClientImplTest extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {

  val deploymentName1: String = randomUUID().toString
  val deploymentName2: String = randomUUID().toString
  val deploymentName3: String = randomUUID().toString
  val deploymentName4: String = randomUUID().toString
  val deploymentName5: String = randomUUID().toString

  private val kubernetesDynamicClient = DynamicKubernetesClientImpl.build()

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(deploymentName1, deploymentName2, deploymentName3, deploymentName4, deploymentName5).map { name =>
      k8s.delete[Deployment](name).withTimeout().recover { case _ => () }
    }).withTimeout().recover{ case _ => () }

    results.valueT

    results.onComplete { _ =>
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }
  }

  behavior of "DynamicKubernetesClientImplTest"


  it should "create a deployment" in { _ =>

    val createdDeployment = Json.parse {
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


    val createdDeploymentResponse = kubernetesDynamicClient.create(JsonRaw(createdDeployment), resourcePlural = "deployments").valueT

    assert(createdDeploymentResponse.metadata.map(_.name).contains(deploymentName1))

    val getDeployment: DynamicKubernetesObject = kubernetesDynamicClient.get(
      deploymentName1,
      apiVersion = "apps/v1",
      resourcePlural = "deployments").valueT

    assert(getDeployment.metadata.map(_.name).contains(deploymentName1))
  }


  it should "update a deployment replicas" in { k8s =>

    //create a deployment
    k8s.create(getNginxDeployment(deploymentName2)).valueT

    val updated = Json.parse {
      s"""
       {
        "kind": "Deployment",
        "apiVersion": "apps/v1",
        "metadata": {
          "name": "$deploymentName2"
        },
        "spec": {
          "replicas": 3,
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
              ],
              "restartPolicy": "Always",
              "dnsPolicy": "ClusterFirst"
            }
          }
        }
      }""".stripMargin
    }


    val updatedDeploymentResponse = kubernetesDynamicClient.update(JsonRaw(updated), resourcePlural = "deployments").valueT

    assert(updatedDeploymentResponse.metadata.map(_.name).contains(deploymentName2))

    val getDeployment: DynamicKubernetesObject = kubernetesDynamicClient.get(
      deploymentName2,
      apiVersion = "apps/v1",
      resourcePlural = "deployments").valueT

    assert((getDeployment.jsonRaw.jsValue \ "spec" \ "replicas").asOpt[Int].contains(3))
  }

  it should "delete a deployment" in { k8s =>

    //create a deployment
    k8s.create(getNginxDeployment(deploymentName3)).valueT

    // delete a deployment
    kubernetesDynamicClient.delete(deploymentName3, apiVersion = "apps/v1", resourcePlural = "deployments").valueT

    Thread.sleep(5000)

    eventually(timeout(30.seconds), interval(3.seconds)) {
      whenReady(kubernetesDynamicClient.get(deploymentName3, apiVersion = "apps/v1", resourcePlural = "deployments").withTimeout().failed) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }
  }

  it should "list deployments" in { k8s =>
    val labels = Map("listDynamic" -> "true")
    //create a deployment
    k8s.create(getNginxDeployment(deploymentName4, labels = labels)).valueT
    k8s.create(getNginxDeployment(deploymentName5, labels = labels)).valueT

    val listOptions = ListOptions(labelSelector = Some(LabelSelector(LabelSelector.IsEqualRequirement("listDynamic", "true"))))
    // list deployments
    val deployments = kubernetesDynamicClient.list(apiVersion = "apps/v1", resourcePlural = "deployments", options = Some(listOptions)).valueT

    assert(deployments.resources.size == 2)
    assert(deployments.resources.flatMap(_.metadata.map(_.name)).toSet === Set(deploymentName4, deploymentName5))
  }


}
