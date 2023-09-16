package skuber

import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import skuber.model.{Container, LabelSelector, Pod}
import skuber.model.apps.v1.Deployment
import skuber.api.client.{DeleteOptions,DeletePropagation,K8SException}

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Failure, Success}

class DeploymentSpec extends K8SFixture with Eventually with Matchers {
  val nginxDeploymentName: String = java.util.UUID.randomUUID().toString

  behavior of "Deployment"

  it should "create a deployment" in { k8s =>
    k8s.create(getNginxDeployment(nginxDeploymentName, "1.7.9")) map { d =>
      assert(d.name == nginxDeploymentName)
    }
  }

  it should "get the newly created deployment" in { k8s =>
    k8s.get[Deployment](nginxDeploymentName) map { d =>
      assert(d.name == nginxDeploymentName)
    }
  }

  it should "upgrade the newly created deployment" in { k8s =>
    k8s.get[Deployment](nginxDeploymentName).flatMap { d =>
      println(s"DEPLOYMENT TO UPDATE ==> $d")
      val updatedDeployment = d.updateContainer(getNginxContainer("1.9.1"))
      k8s.update(updatedDeployment).flatMap { _ =>
        eventually(timeout(200.seconds), interval(5.seconds)) {
          val retrieveDeployment=k8s.get[Deployment](nginxDeploymentName)
          ScalaFutures.whenReady(retrieveDeployment, timeout(2.seconds), interval(1.second)) { deployment =>
            deployment.status.get.updatedReplicas shouldBe 1
          }
        }
      }
    }
  }

  it should "delete a deployment" in { k8s =>
    k8s.deleteWithOptions[Deployment](nginxDeploymentName, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground))).map { _ =>
      eventually(timeout(200.seconds), interval(3.seconds)) {
        val retrieveDeployment = k8s.get[Deployment](nginxDeploymentName)
        val deploymentRetrieved=Await.ready(retrieveDeployment, 2.seconds).value.get
        deploymentRetrieved match {
          case s: Success[_] => assert(false)
          case Failure(ex) => ex match {
            case ex: K8SException if ex.status.code.contains(404) => assert(true)
            case _ => assert(false)
          }
        }
      }
    }
  }

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)

  def getNginxDeployment(name: String, version: String): Deployment = {
    import LabelSelector.dsl._
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(nginxTemplate).withLabelSelector("app" is "nginx")
  }
}
