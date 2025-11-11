package skuber

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import skuber.api.client.{DeleteOptions, DeletePropagation, K8SException}
import skuber.model.apps.v1.Deployment
import skuber.model.{Container, LabelSelector, Pod}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.{postfixOps, reflectiveCalls}
import scala.util.{Failure, Success}

/**
 * Shared integration tests for Deployment operations that work with both Akka and Pekko clients.
 * The concrete fixture (AkkaK8SFixture or PekkoK8SFixture) is mixed in via build configuration.
 */
abstract class DeploymentSpec extends K8SFixture with Eventually with Matchers {
  val nginxDeploymentName: String = java.util.UUID.randomUUID().toString

  behavior of "Deployment"

  it should "create a deployment" in {
    withK8sClient { k8s =>
      k8s.create(getNginxDeployment(nginxDeploymentName)) map { d =>
        assert(d.name == nginxDeploymentName)
      }
    }
  }

  it should "get the newly created deployment" in {
    withK8sClient { k8s =>
      k8s.get[Deployment](nginxDeploymentName) map { d =>
        assert(d.name == nginxDeploymentName)
      }
    }
  }

  it should "upgrade the newly created deployment" in {
    withK8sClient { k8s =>
      k8s.get[Deployment](nginxDeploymentName).flatMap { d =>
        val updatedDeployment = d.updateContainer(getNginxContainer("1.9.1"))
        k8s.update(updatedDeployment).flatMap { _ =>
          eventually(timeout(200.seconds), interval(5.seconds)) {
            val retrieveDeployment = k8s.get[Deployment](nginxDeploymentName)
            ScalaFutures.whenReady(retrieveDeployment, timeout(2.seconds), interval(1.second)) { deployment =>
              deployment.status.get.updatedReplicas shouldBe 1
            }
          }
        }
      }
    }
  }

  it should "delete a deployment" in {
    withK8sClient { k8s =>
      k8s.deleteWithOptions[Deployment](nginxDeploymentName, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground))).map { _ =>
        eventually(timeout(200.seconds), interval(3.seconds)) {
          val retrieveDeployment = k8s.get[Deployment](nginxDeploymentName)
          val deploymentRetrieved = Await.ready(retrieveDeployment, 2.seconds).value.get
          deploymentRetrieved match {
            case s: Success[_] => fail("Deleted deployment still exists")
            case Failure(ex) => ex match {
              case ex: K8SException if ex.status.code.contains(404) => succeed
              case _ => fail(s"Unexpected exception: ${ex.getMessage}")
            }
          }
        }
      }
    }
  }
}