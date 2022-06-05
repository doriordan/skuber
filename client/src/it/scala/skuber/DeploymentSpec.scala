package skuber

import java.util.UUID.randomUUID
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers}
import skuber.apps.v1.Deployment
import scala.concurrent.Future
import scala.concurrent.duration._
import skuber.FutureUtil.FutureOps

class DeploymentSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {


  val deploymentName1: String = randomUUID().toString
  val deploymentName2: String = randomUUID().toString
  val deploymentName3: String = randomUUID().toString

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(deploymentName1, deploymentName2, deploymentName3).map { name =>
      k8s.delete[Deployment](name).withTimeout().recover { case _ => () }
    })

    results.futureValue

    results.onComplete { r =>
      k8s.close
      system.terminate()
    }

  }

  behavior of "Deployment"


  it should "create a deployment" in { k8s =>

    val createdDeployment = k8s.create(getNginxDeployment(deploymentName1, "1.7.9")).withTimeout().futureValue
    assert(createdDeployment.name == deploymentName1)

    val getDeployment = k8s.get[Deployment](deploymentName1).withTimeout().futureValue
    assert(getDeployment.name == deploymentName1)
  }


  it should "upgrade the newly created deployment" in { k8s =>
    k8s.create(getNginxDeployment(deploymentName2, "1.7.9")).withTimeout().futureValue
    Thread.sleep(5000)
    val getDeployment = k8s.get[Deployment](deploymentName2).withTimeout().futureValue
    println(s"DEPLOYMENT TO UPDATE ==> $getDeployment")

    val updatedDeployment = getDeployment.updateContainer(getNginxContainer("1.9.1"))

    k8s.update(updatedDeployment).withTimeout().futureValue

    val deployment = k8s.get[Deployment](deploymentName2).withTimeout().futureValue

    val actualImages: List[String] = deployment.spec.flatMap(_.template.spec.map(_.containers.map(_.image))).toList.flatten
    actualImages shouldBe List("nginx:1.9.1")

  }


  it should "delete a deployment" in { k8s =>
    val d = k8s.create(getNginxDeployment(deploymentName3, "1.7.9")).withTimeout().futureValue
    assert(d.name == deploymentName3)

    k8s.deleteWithOptions[Deployment](deploymentName3, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground))).withTimeout().futureValue
    eventually(timeout(20.seconds), interval(3.seconds)) {
      whenReady(
        k8s.get[Deployment](deploymentName3).withTimeout().failed
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
