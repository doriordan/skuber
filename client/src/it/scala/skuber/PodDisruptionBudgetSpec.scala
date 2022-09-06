package skuber

import java.util.UUID.randomUUID
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import skuber.FutureUtil.FutureOps
import skuber.LabelSelector.IsEqualRequirement
import skuber.apps.v1.Deployment
import skuber.policy.v1beta1.PodDisruptionBudget
import skuber.policy.v1beta1.PodDisruptionBudget._
import scala.concurrent.Future
import scala.concurrent.duration._

class PodDisruptionBudgetSpec extends K8SFixture with Matchers with BeforeAndAfterAll with ScalaFutures with Eventually {
  behavior of "PodDisruptionBudget"
  val budget1: String = randomUUID().toString
  val budget2: String = randomUUID().toString
  val budget3: String = randomUUID().toString

  val deployment1: String = randomUUID().toString
  val deployment2: String = randomUUID().toString
  val deployment3: String = randomUUID().toString

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results1 = {
      val futures = Future.sequence(List(budget1, budget2, budget3).map(name => k8s.delete[PodDisruptionBudget](name).withTimeout())).withTimeout()
      futures.recover { case _ =>
        ()
      }
    }

    val results2 = {
      val futures = Future.sequence(List(deployment1, deployment2, deployment3).map(name => k8s.delete[Deployment](name).withTimeout())).withTimeout()
      futures.recover { case _ =>
        ()
      }
    }

    results1.futureValue
    results2.futureValue

    for {
      _ <- results1
      _ <- results2
    } yield {
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }

  }

  it should "create a PodDisruptionBudget" in { k8s =>
    println("START: create a PodDisruptionBudget")
    k8s.create(getNginxDeployment(deployment1, "1.7.9")).valueT
    import LabelSelector.dsl._
    val labelSelector = LabelSelector(IsEqualRequirement("app", "nginx"))
    val result = k8s.create(PodDisruptionBudget(budget1).withMinAvailable(Left(1)).withLabelSelector(labelSelector)).valueT
    Thread.sleep(5000)

    println("FINISH: create a PodDisruptionBudget")

    assert(result.spec.contains(PodDisruptionBudget.Spec(None, Some(1), Some(labelSelector))))
    assert(result.name == budget1)
  }

  it should "update a PodDisruptionBudget" in { k8s =>
    println("START: update a PodDisruptionBudget")
    val deployment = k8s.create(getNginxDeployment(deployment2, "1.7.9")).withTimeout()
    deployment.futureValue
    import LabelSelector.dsl._
    val labelSelector = LabelSelector(IsEqualRequirement("app", "nginx"))
    val firstPdb = k8s.create(PodDisruptionBudget(budget2).withMinAvailable(Left(1)).withLabelSelector(labelSelector)).valueT
    Thread.sleep(5000)

    eventually(timeout(30.seconds), interval(3.seconds)) {
      val finalPdb = k8s.get[PodDisruptionBudget](firstPdb.name).withTimeout().map {
        updatedPdb => updatedPdb.copy(metadata = updatedPdb.metadata.copy(creationTimestamp = None, selfLink = "", uid = ""))
      }.futureValue

      val updatedPdb = k8s.update(finalPdb).valueT

      println("FINISH: update a PodDisruptionBudget")
      val labelSelector = LabelSelector(IsEqualRequirement("app", "nginx"))
      assert(updatedPdb.spec.contains(PodDisruptionBudget.Spec(None, Some(1), Some(labelSelector))))
      assert(updatedPdb.name == budget2)
    }

  }


  it should "delete a PodDisruptionBudget" in { k8s =>
    println("START: delete a PodDisruptionBudget")
    k8s.create(getNginxDeployment(deployment3, "1.7.9")).valueT
    import LabelSelector.dsl._
    val labelSelector = LabelSelector(IsEqualRequirement("app", "nginx"))
    val pdb = k8s.create(PodDisruptionBudget(budget3).withMinAvailable(Left(1)).withLabelSelector(labelSelector)).valueT
    k8s.delete[PodDisruptionBudget](pdb.name).valueT
    Thread.sleep(5000)

    eventually(timeout(30.seconds), interval(3.seconds)) {
      whenReady(k8s.get[PodDisruptionBudget](pdb.name).withTimeout().failed) { result =>
        println("FINISH: delete a PodDisruptionBudget")
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }

  }

}
