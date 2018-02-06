package skuber

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import skuber.ext.Deployment
import skuber.json.ext.format._
import scala.concurrent.duration._

import scala.concurrent.Future

class DeploymentSpec extends AsyncFlatSpec with Eventually with Matchers {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  val k8s = k8sInit

  val nginxDeploymentName = java.util.UUID.randomUUID().toString


  behavior of "Deployment"

  val createDeploymentFuture = createNginxDeployment(nginxDeploymentName)

  it should "create and eventually delete a deployment" in {
    createDeploymentFuture flatMap { d =>
      assert(d.name == nginxDeploymentName)
      deleteNginxDeployment(nginxDeploymentName).map { _ =>
        eventually(timeout(3 seconds), interval(3 seconds)) {
          val f: Future[Deployment] = k8s.get[Deployment](nginxDeploymentName)
          ScalaFutures.whenReady(f.failed) { e =>
            e shouldBe a[K8SException]
          }
        }
      }
    }
  }

  def createNginxDeployment(name: String): Future[Deployment] = {
    val nginxContainer = Container(name = "nginx", image="nginx").exposePort(80)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    val nginxDeployment = Deployment(name).withTemplate(nginxTemplate)

    k8s.create(nginxDeployment)
  }

  def deleteNginxDeployment(name: String): Future[Unit] = {
    k8s.delete[Deployment](name)
  }
}
