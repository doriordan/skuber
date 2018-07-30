package skuber

import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Keep, Sink}
import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import skuber.apps.v1.{Deployment, DeploymentList}

import scala.concurrent.duration._
import scala.language.postfixOps

class WatchContinuouslySpec extends K8SFixture with Eventually with Matchers {
  val nginxDeploymentName: String = java.util.UUID.randomUUID().toString

  behavior of "WatchContinuously"

  it should "watch the custom resources" in { k8s =>
    import skuber.api.client.{EventType, WatchEvent}
    import scala.collection.mutable.ListBuffer

    val deployment = getNginxDeployment(nginxDeploymentName, "1.7.9")

    val trackedEvents = ListBuffer.empty[WatchEvent[Deployment]]
    val trackEvents: Sink[WatchEvent[Deployment], _] = Sink.foreach { event =>
      if(event._object.name == nginxDeploymentName && (event._type == EventType.ADDED || event._type == EventType.DELETED)) {
        trackedEvents += event
      }
    }

    def createSource() = k8s.list[DeploymentList].map { l =>
      k8s.watchAllContinuously[Deployment](Some(l.resourceVersion))
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(trackEvents)(Keep.both)
        .run()
    }

    val killSwitchFut = for {
      (kill, _) <- createSource()
      testResource <- k8s.create(deployment)
      deleted <- k8s.delete[Deployment](nginxDeploymentName)
    } yield kill

    eventually(timeout(200 seconds), interval(3 seconds)) {
      trackedEvents.size shouldBe 2
      trackedEvents.head._type shouldBe EventType.ADDED
      trackedEvents.head._object.name shouldBe deployment.name
      trackedEvents(1)._type shouldBe EventType.DELETED
    }

    // cleanup
    killSwitchFut.map { killSwitch =>
      killSwitch.shutdown()
      assert(true)
    }
  }

  def getNginxDeployment(name: String, version: String): Deployment = {
    import LabelSelector.dsl._
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(nginxTemplate).withLabelSelector("app" is "nginx")
  }

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)
}
