package skuber

import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import skuber.api.client.K8SException
import skuber.model.LabelSelector.dsl._
import skuber.model.apps.v1.Deployment
import skuber.model.policy.v1.PodDisruptionBudget
import skuber.model.{Container, LabelSelector, Pod}

import scala.language.{postfixOps, reflectiveCalls}

/**
 * Shared integration tests for PodDisruptionBudget operations that work with both Akka and Pekko clients.
 * The concrete fixture (AkkaK8SFixture or PekkoK8SFixture) is mixed in via build configuration.
 */
abstract class PodDisruptionBudgetSpec extends K8SFixture[_, _, _] with Eventually with Matchers {
  behavior of "PodDisruptionBudget"

  it should "create a PodDisruptionBudget" in { k8s =>
    val name: String = java.util.UUID.randomUUID().toString
    k8s.create(getNginxDeployment(name, "1.7.9")) flatMap { _ =>
      k8s.create(PodDisruptionBudget(name)
        .withMinAvailable(Left(1))
        .withLabelSelector("app" is "nginx")
      ).map { result =>
        assert(result.spec.contains(PodDisruptionBudget.Spec(None, Some(Left(1)), Some("app" is "nginx"))))
        assert(result.name == name)
      }
    }
  }

  it should "update a PodDisruptionBudget" in { k8s =>
    val name: String = java.util.UUID.randomUUID().toString
    k8s.create(getNginxDeployment(name, "1.7.9")) flatMap { _ =>
      k8s.create(PodDisruptionBudget(name)
        .withMinAvailable(Left(1))
        .withLabelSelector("app" is "nginx")
      ).flatMap(pdb =>
        eventually(
          k8s.get[PodDisruptionBudget](pdb.name).flatMap { updatedPdb =>
            k8s.update(updatedPdb).map { result => //PodDisruptionBudget are immutable at the moment.
              assert(result.spec.contains(PodDisruptionBudget.Spec(None, Some(Left(1)), Some("app" is "nginx"))))
              assert(result.name == name)
            }
          }
        )
      )
    }
  }

  it should "delete a PodDisruptionBudget" in { k8s =>
    val name: String = java.util.UUID.randomUUID().toString
    k8s.create(getNginxDeployment(name, "1.7.9")) flatMap { d =>
      import LabelSelector.dsl._
      k8s.create(PodDisruptionBudget(name)
        .withMinAvailable(Left(1))
        .withLabelSelector("app" is "nginx")
      ).flatMap { pdb =>
        k8s.delete[PodDisruptionBudget](pdb.name).flatMap { deleteResult =>
          k8s.get[PodDisruptionBudget](pdb.name).map { x =>
            assert(false)
          } recoverWith {
            case ex: K8SException if ex.status.code.contains(404) => assert(true)
            case _ => assert(false)
          }
        }
      }
    }
  }

  def getNginxDeployment(name: String, version: String): Deployment = {
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(nginxTemplate).withLabelSelector("app" is "nginx")
  }

  def getNginxContainer(version: String): Container = {
    Container(name = "nginx", image = "nginx:" + version).exposePort(80)
  }
}