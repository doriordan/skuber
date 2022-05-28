package skuber

import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.{BeforeAndAfterAll, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import skuber.apps.v1.Deployment
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class DeploymentSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {


  val deploymentName1: String = java.util.UUID.randomUUID().toString
  val deploymentName2: String = java.util.UUID.randomUUID().toString
  val deploymentName3: String = java.util.UUID.randomUUID().toString

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(deploymentName1, deploymentName2, deploymentName3).map { name =>
      k8s.delete[Deployment](name).recover { case _ => () }
    })

    results.futureValue

    results.onComplete { r =>
      k8s.close
    }

    super.afterAll()
  }

  behavior of "Deployment"


  it should "create a deployment" in { k8s =>

    val createdDeployment = k8s.create(getNginxDeployment(deploymentName1, "1.7.9")).futureValue
    assert(createdDeployment.name == deploymentName1)

    val getDeployment = k8s.get[Deployment](deploymentName1).futureValue
    assert(getDeployment.name == deploymentName1)
  }


  it should "upgrade the newly created deployment" in { k8s =>
    k8s.create(getNginxDeployment(deploymentName2, "1.7.9")).futureValue
    Thread.sleep(5000)
    val getDeployment = k8s.get[Deployment](deploymentName2).futureValue
    println(s"DEPLOYMENT TO UPDATE ==> $getDeployment")

    val updatedDeployment = getDeployment.updateContainer(getNginxContainer("1.9.1"))

    k8s.update(updatedDeployment).futureValue

    val deployment = k8s.get[Deployment](deploymentName2).futureValue

    val actualImages: List[String] = deployment.spec.flatMap(_.template.spec.map(_.containers.map(_.image))).toList.flatten
    actualImages shouldBe List("nginx:1.9.1")

  }


  it should "delete a deployment" in { k8s =>
    val d = k8s.create(getNginxDeployment(deploymentName3, "1.7.9")).futureValue
    assert(d.name == deploymentName3)

    k8s.deleteWithOptions[Deployment](deploymentName3, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground))).futureValue
    eventually(timeout(20.seconds), interval(3.seconds)) {
      whenReady(
        k8s.get[Deployment](deploymentName3).failed
      ) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
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
