package skuber

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.language.postfixOps

import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}

import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.scaladsl.{Keep, Sink}

import skuber.model.{Container, LabelSelector, Pod}
import skuber.model.apps.v1.{Deployment, DeploymentList}

class WatchContinuouslySpec extends K8SFixture with Eventually with Matchers with ScalaFutures {
  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(200, Seconds), interval = Span(5, Seconds))

  behavior of "WatchContinuously"

  it should "continuously watch changes on a resource - deployments" in { k8s =>
    import skuber.api.client.EventType

    val deploymentOneName = java.util.UUID.randomUUID().toString
    val deploymentTwoName = java.util.UUID.randomUUID().toString
    val deploymentOne = getNginxDeployment(deploymentOneName, "1.7.9")
    val deploymentTwo = getNginxDeployment(deploymentTwoName, "1.7.9")

    val stream = k8s.list[DeploymentList].map { l =>
      k8s.watchAllContinuously[Deployment](Some(l.resourceVersion))
        .viaMat(KillSwitches.single)(Keep.right)
        .filter(event => event._object.name == deploymentOneName || event._object.name == deploymentTwoName)
        .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
        .toMat(Sink.collection)(Keep.both)
        .run()
    }

    // Wait for watch to be confirmed before performing the actions that create new events to be watched
    Await.result(stream, 5.seconds)

    //Create first deployment and delete it.
    k8s.create(deploymentOne).futureValue.name shouldBe deploymentOneName
    eventually {
      k8s.get[Deployment](deploymentOneName).futureValue.status.get.availableReplicas shouldBe 1
    }
    k8s.delete[Deployment](deploymentOneName).futureValue

    /*
     * Request times for request is defaulted to 30 seconds.
     * The idle timeout is also defaulted to 60 seconds.
     * This will ensure multiple requests are performed by
     * the source including empty responses
     */
    pause(62.seconds)

    //Create second deployment and delete it.
    k8s.create(deploymentTwo).futureValue.name shouldBe deploymentTwoName
    eventually {
      k8s.get[Deployment](deploymentTwoName).futureValue.status.get.availableReplicas shouldBe 1
    }
    k8s.delete[Deployment](deploymentTwoName).futureValue

    pause(10.seconds) // enable enough time for second deployment events to be consumed

    // cleanup
    stream.map { killSwitch =>
      killSwitch._1.shutdown()
    }

    stream.futureValue._2.futureValue.toList.map { d =>
      (d._type, d._object.name)
    } shouldBe List(
      (EventType.ADDED, deploymentOneName),
      (EventType.DELETED, deploymentOneName),
      (EventType.ADDED, deploymentTwoName),
      (EventType.DELETED, deploymentTwoName)
    )
  }

  it should "continuously watch changes on a named resource obj from the beginning - deployment" in { k8s =>
    import skuber.api.client.EventType

    val deploymentName = java.util.UUID.randomUUID().toString
    val deployment = getNginxDeployment(deploymentName, "1.7.9")

    k8s.create(deployment).futureValue.name shouldBe deploymentName
    eventually {
      k8s.get[Deployment](deploymentName).futureValue.status.get.availableReplicas shouldBe 1
    }

    val stream = k8s.get[Deployment](deploymentName).map { d =>
      k8s.watchContinuously[Deployment](d)
        .viaMat(KillSwitches.single)(Keep.right)
        .filter(event => event._object.name == deploymentName)
        .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
        .toMat(Sink.collection)(Keep.both)
        .run()
    }

    /*
     * Request times for request is defaulted to 30 seconds.
     * The idle timeout is also defaulted to 60 seconds.
     * This will ensure multiple requests are performed by
     * the source including empty responses
     */
    pause(62.seconds)

    k8s.delete[Deployment](deploymentName).futureValue

    pause(10.seconds) // enable enough time for delete event to be consumed

    // cleanup
    stream.map { killSwitch =>
      killSwitch._1.shutdown()
    }

    val f1 = stream.futureValue

    val f2 = f1._2.futureValue

    f2.toList.map { d =>
      (d._type, d._object.name)
    } shouldBe List(
      (EventType.ADDED, deploymentName),
      (EventType.DELETED, deploymentName)
    )
  }

  it should "continuously watch changes on a named resource from a point in time - deployment" in { k8s =>
    import skuber.api.client.EventType

    val deploymentName = java.util.UUID.randomUUID().toString
    val deployment = getNginxDeployment(deploymentName, "1.7.9")

    k8s.create(deployment).futureValue.name shouldBe deploymentName
    eventually {
      k8s.get[Deployment](deploymentName).futureValue.status.get.availableReplicas shouldBe 1
    }

    val stream = k8s.get[Deployment](deploymentName).map { d =>
      k8s.watchContinuously[Deployment](deploymentName, Some(d.resourceVersion))
        .viaMat(KillSwitches.single)(Keep.right)
        .filter(event => event._object.name == deploymentName)
        .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
        .toMat(Sink.collection)(Keep.both)
        .run()
    }

    /*
     * Request times for request is defaulted to 30 seconds.
     * The idle timeout is also defaulted to 60 seconds.
     * This will ensure multiple requests are performed by
     * the source including empty responses
     */
    pause(62.seconds)

    k8s.delete[Deployment](deploymentName).futureValue

    pause(10.seconds) // enable enough time for delete event to be consumed

    // cleanup
    stream.map { killSwitch =>
      killSwitch._1.shutdown()
    }

    stream.futureValue._2.futureValue.toList.map { d =>
      (d._type, d._object.name)
    } shouldBe List(
      (EventType.DELETED, deploymentName)
    )
  }

  def pause(length: Duration): Unit ={
    Thread.sleep(length.toMillis)
  }

  def getNginxDeployment(name: String, version: String): Deployment = {
    import LabelSelector.dsl._
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(nginxTemplate).withLabelSelector("app" is "nginx")
  }

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)
}
