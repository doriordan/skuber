package skuber

import java.util.UUID.randomUUID
import akka.stream.KillSwitches
import akka.stream.scaladsl.{Keep, Sink}
import org.scalatest.{BeforeAndAfterAll, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import skuber.apps.v1.{Deployment, DeploymentList}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class WatchContinuouslySpec extends K8SFixture with Eventually with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(200, Seconds), interval = Span(5, Seconds))

  behavior of "WatchContinuously"
  val deployment1: String = randomUUID().toString
  val deployment2: String = randomUUID().toString
  val deployment3: String = randomUUID().toString

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(deployment1, deployment2, deployment3).map { name =>
      k8s.delete[Deployment](name).recover { case _ => () }
    })

    results.futureValue

    results.onComplete { r =>
      k8s.close
    }

  }

  it should "continuously watch changes on a resource - deployments" in { k8s =>
    import skuber.api.client.EventType

    val deploymentOne = getNginxDeployment(deployment1, "1.7.9")
    val deploymentTwo = getNginxDeployment(deployment2, "1.7.9")

    val stream = k8s.list[DeploymentList].map { l =>
      k8s.watchAllContinuously[Deployment](Some(l.resourceVersion))
        .viaMat(KillSwitches.single)(Keep.right)
        .filter(event => event._object.name == deployment1 || event._object.name == deployment2)
        .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
        .toMat(Sink.collection)(Keep.both)
        .run()
    }

    // Wait for watch to be confirmed before performing the actions that create new events to be watched
    stream.futureValue

    //Create first deployment and delete it.
    k8s.create(deploymentOne).futureValue.name shouldBe deployment1
    eventually {
      k8s.get[Deployment](deployment1).futureValue.status.get.availableReplicas shouldBe 1
    }
    k8s.delete[Deployment](deployment1).futureValue

    /*
     * Request times for request is defaulted to 30 seconds.
     * The idle timeout is also defaulted to 60 seconds.
     * This will ensure multiple requests are performed by
     * the source including empty responses
     */
    pause(10.seconds)

    //Create second deployment and delete it.
    k8s.create(deploymentTwo).futureValue.name shouldBe deployment2
    eventually {
      k8s.get[Deployment](deployment2).futureValue.status.get.availableReplicas shouldBe 1
    }
    k8s.delete[Deployment](deployment2).futureValue

    // cleanup
    stream.map { killSwitch =>
      killSwitch._1.shutdown()
    }

    stream.futureValue._2.futureValue.toList.map { d =>
      (d._type, d._object.name)
    } shouldBe List(
      (EventType.ADDED, deployment1),
      (EventType.DELETED, deployment1),
      (EventType.ADDED, deployment2),
      (EventType.DELETED, deployment2)
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
    pause(20.seconds)

    k8s.delete[Deployment](deploymentName).futureValue

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

  it should "continuously watch changes on a named resource from the beginning - deployment" in { k8s =>
    import skuber.api.client.EventType

    val deployment = getNginxDeployment(deployment3, "1.7.9")

    k8s.create(deployment).futureValue.name shouldBe deployment3
    eventually {
      k8s.get[Deployment](deployment3).futureValue.status.get.availableReplicas shouldBe 1
    }

    val stream = k8s.get[Deployment](deployment3).map { d =>
      k8s.watchContinuously[Deployment](deployment3, None)
        .viaMat(KillSwitches.single)(Keep.right)
        .filter(event => event._object.name == deployment3)
        .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
        .toMat(Sink.collection)(Keep.both)
        .run()
    }

    /*
     * Request times for request is defaulted to 30 seconds.
     * This will ensure multiple requests are performed by
     * the source including empty responses
     */
    pause(20.seconds)

    k8s.delete[Deployment](deployment3).futureValue

    // cleanup
    stream.map { killSwitch =>
      killSwitch._1.shutdown()
    }

    stream.futureValue._2.futureValue.toList.map { d =>
      (d._type, d._object.name)
    } shouldBe List(
      (EventType.ADDED, deployment3),
      (EventType.DELETED, deployment3)
    )
  }

  it should "continuously watch changes on a named resource from a point in time - deployment" in { k8s =>
    import skuber.api.client.EventType


    val deployment = getNginxDeployment(deployment3, "1.7.9")

    k8s.create(deployment).futureValue.name shouldBe deployment3
    eventually {
      k8s.get[Deployment](deployment3).futureValue.status.get.availableReplicas shouldBe 1
    }

    val stream = k8s.get[Deployment](deployment3).map { d =>
      k8s.watchContinuously[Deployment](deployment3, Some(d.resourceVersion))
        .viaMat(KillSwitches.single)(Keep.right)
        .filter(event => event._object.name == deployment3)
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
    pause(20.seconds)

    k8s.delete[Deployment](deployment3).futureValue

    // cleanup
    stream.map { killSwitch =>
      killSwitch._1.shutdown()
    }

    stream.futureValue._2.futureValue.toList.map { d =>
      (d._type, d._object.name)
    } shouldBe List(
      (EventType.DELETED, deployment3)
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
