package skuber

import java.util.UUID.randomUUID
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import skuber.FutureUtil.FutureOps
import skuber.api.client.KubernetesClient
import skuber.apps.v1.Deployment.deployDef
import skuber.apps.v1.{Deployment, DeploymentList}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try


class DeploymentSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {


  val deploymentName1: String = randomUUID().toString
  val deploymentName2: String = randomUUID().toString
  val deploymentName3: String = randomUUID().toString

  val deploymentSpecific1: String = randomUUID().toString
  val deploymentSpecific2: String = randomUUID().toString
  val deploymentSpecific3: String = randomUUID().toString
  val deploymentSpecific41: String = randomUUID().toString
  val deploymentSpecific42: String = randomUUID().toString
  val deploymentSpecific51: String = randomUUID().toString
  val deploymentSpecific52: String = randomUUID().toString

  val namespace1: String = randomUUID().toString
  val namespace2: String = randomUUID().toString
  val namespace3: String = randomUUID().toString
  val namespace4: String = randomUUID().toString
  val namespace5: String = randomUUID().toString


  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(deploymentName1, deploymentName2,
      deploymentName3, deploymentSpecific41, deploymentSpecific42, deploymentSpecific51, deploymentSpecific52).map { name =>
      k8s.delete[Deployment](name).withTimeout().recover { case _ => () }
    }).withTimeout()

    results.futureValue

    results.onComplete { _ =>
      List(namespace1, namespace2, namespace3, namespace4, namespace5).foreach { name =>
        deleteNamespace(name, k8s)
      }
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }

  }

  behavior of "Deployment"


  it should "create a deployment" in { k8s =>

    val createdDeployment = k8s.create(getNginxDeployment(deploymentName1, "1.7.9")).valueT
    assert(createdDeployment.name == deploymentName1)

    val getDeployment = k8s.get[Deployment](deploymentName1).valueT
    assert(getDeployment.name == deploymentName1)
  }

  it should "create a deployment in specific namespace" in { k8s =>
    createNamespace(namespace1, k8s)
    val createdDeployment = k8s.create(getNginxDeployment(deploymentSpecific1, "1.7.9"), Some(namespace1)).valueT
    createdDeployment.name shouldBe deploymentSpecific1

    val getDeployment = k8s.get[Deployment](deploymentSpecific1, Some(namespace1)).valueT
    getDeployment.name shouldBe deploymentSpecific1

    val getDeploymentOpt = k8s.getOption[Deployment](deploymentSpecific1, Some(namespace1)).valueT
    getDeploymentOpt.map(_.name) shouldBe Some(deploymentSpecific1)
  }


  it should "update a deployment in specific namespace" in { k8s =>
    createNamespace(namespace2, k8s)
    val createdDeployment = k8s.create(getNginxDeployment(deploymentSpecific2, "1.7.9"), Some(namespace2)).valueT
    createdDeployment.name shouldBe deploymentSpecific2

    val expectedReplicas = 2

    eventually(timeout(30.seconds), interval(3.seconds)) {
      val getDeployment = k8s.get[Deployment](deploymentSpecific2, Some(namespace2)).valueT
      getDeployment.name shouldBe deploymentSpecific2

      val deploymentToUpdate = getDeployment.withReplicas(expectedReplicas)
      k8s.update[Deployment](deploymentToUpdate, Some(namespace2)).valueT

      val getDeploymentUpdated = k8s.get[Deployment](deploymentSpecific2, Some(namespace2)).valueT
      getDeploymentUpdated.spec.flatMap(_.replicas) shouldBe Some(expectedReplicas)
    }
  }




  it should "upgrade the newly created deployment" in { k8s =>
    k8s.create(getNginxDeployment(deploymentName2, "1.7.9")).valueT
    Thread.sleep(5000)
    val getDeployment = k8s.get[Deployment](deploymentName2).valueT
    println(s"DEPLOYMENT TO UPDATE ==> $getDeployment")

    val updatedDeployment = getDeployment.updateContainer(getNginxContainer("1.9.1"))
    eventually(timeout(30.seconds), interval(3.seconds)) {
      Try {
        k8s.update(updatedDeployment).valueT
      }.recover { case _ => () }

      val deployment = k8s.get[Deployment](deploymentName2).valueT

      val actualImages: List[String] = deployment.spec.flatMap(_.template.spec.map(_.containers.map(_.image))).toList.flatten
      actualImages shouldBe List("nginx:1.9.1")
    }

  }


  it should "delete a deployment" in { k8s =>
    val d = k8s.create(getNginxDeployment(deploymentName3, "1.7.9")).valueT
    assert(d.name == deploymentName3)

    k8s.delete[Deployment](deploymentName3).valueT

    eventually(timeout(30.seconds), interval(3.seconds)) {
      whenReady(k8s.get[Deployment](deploymentName3).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }
  }

  it should "delete a deployment in specific namespace" in { k8s =>
    createNamespace(namespace3, k8s)

    val d = k8s.create(getNginxDeployment(deploymentSpecific3, "1.7.9"), Some(namespace3)).valueT
    assert(d.name == deploymentSpecific3)

    k8s.delete[Deployment](deploymentSpecific3, namespace = Some(namespace3)).valueT

    eventually(timeout(30.seconds), interval(3.seconds)) {
      whenReady(k8s.get[Deployment](deploymentSpecific3, Some(namespace3)).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }
  }

  it should "deleteAll - delete all deployments in specific namespace" in { k8s =>
    createNamespace(namespace4, k8s)

    k8s.create(getNginxDeployment(deploymentSpecific41, "1.7.9"), Some(namespace4)).valueT
    k8s.create(getNginxDeployment(deploymentSpecific42, "1.7.9"), Some(namespace4)).valueT


    k8s.deleteAll[DeploymentList](namespace = Some(namespace4)).valueT

    eventually(timeout(30.seconds), interval(3.seconds)) {
      whenReady(k8s.get[Deployment](deploymentSpecific41).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }

      whenReady(k8s.get[Deployment](deploymentSpecific42).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }
  }

  it should "list deployment in specific namespace" in { k8s =>
    createNamespace(namespace5, k8s)

    k8s.create(getNginxDeployment(deploymentSpecific51, "1.7.9"), Some(namespace5)).valueT
    k8s.create(getNginxDeployment(deploymentSpecific52, "1.7.9"), Some(namespace5)).valueT

    val expectedDeploymentList = List(deploymentSpecific51, deploymentSpecific52)
    val actualDeploymentList =
      k8s.list[DeploymentList](namespace = Some(namespace5)).valueT.map(_.name)

    actualDeploymentList should contain theSameElementsAs expectedDeploymentList
  }

}
