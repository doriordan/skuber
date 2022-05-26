package skuber

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers}
import skuber.apps.v1.Deployment
import skuber.policy.v1beta1.PodDisruptionBudget
import skuber.policy.v1beta1.PodDisruptionBudget._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class PodDisruptionBudgetSpec extends K8SFixture with Matchers with BeforeAndAfterAll with ScalaFutures {
  behavior of "PodDisruptionBudget"
  val name1: String = java.util.UUID.randomUUID().toString
  val name2: String = java.util.UUID.randomUUID().toString
  val name3: String = java.util.UUID.randomUUID().toString
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)
  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = {
      val futures = Future.sequence(List(name1, name2, name3).map(name => k8s.delete[Deployment](name)))
      futures.recover { case _ =>
        ()
      }
    }

    results.onComplete { _ =>
      k8s.close
    }

    super.afterAll()
  }

  it should "create a PodDisruptionBudget" in { k8s =>
    k8s.create(getNginxDeployment(name1, "1.7.9")) flatMap { d =>
      import LabelSelector.dsl._
      k8s.create(PodDisruptionBudget(name1)
        .withMinAvailable(Left(1))
        .withLabelSelector("app" is "nginx")
      ).map { result =>
        assert(result.spec.contains(PodDisruptionBudget.Spec(None, Some(1), Some("app" is "nginx"))))
        assert(result.name == name1)
      }
    }
  }

  it should "update a PodDisruptionBudget" in { k8s =>
    val deployment = k8s.create(getNginxDeployment(name2, "1.7.9"))
    deployment.futureValue
    import LabelSelector.dsl._
    val firstPdb = k8s.create(PodDisruptionBudget(name2).withMinAvailable(Left(1)).withLabelSelector("app" is "nginx")).futureValue

    Thread.sleep(5000)

    val finalPdb = k8s.get[PodDisruptionBudget](firstPdb.name).map {
      updatedPdb => updatedPdb.copy(metadata = updatedPdb.metadata.copy(creationTimestamp = None, selfLink = "", uid = ""))
    }.futureValue

    val updatedPdb = k8s.update(finalPdb).futureValue
    assert(updatedPdb.spec.contains(PodDisruptionBudget.Spec(None, Some(1), Some("app" is "nginx"))))
    assert(updatedPdb.name == name2)

  }


  it should "delete a PodDisruptionBudget" in { k8s =>
    k8s.create(getNginxDeployment(name3, "1.7.9")) flatMap { d =>
      import LabelSelector.dsl._
      k8s.create(PodDisruptionBudget(name3)
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
    import LabelSelector.dsl._
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(nginxTemplate).withLabelSelector("app" is "nginx")
  }

  def getNginxContainer(version: String): Container = {
    Container(name = "nginx", image = "nginx:" + version).exposePort(80)
  }
}
