package skuber

import java.util.UUID.randomUUID
import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import skuber.FutureUtil.FutureOps
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
  val deployment4: String = randomUUID().toString
  val deployment5: String = randomUUID().toString
  val namespace5: String = randomUUID().toString

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(deployment1, deployment2, deployment3, deployment4, deployment5).map { name =>
      k8s.delete[Deployment](name).withTimeout().recover { case _ => () }
    }).withTimeout()

    results.futureValue

    results.onComplete { _ =>
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }

  }

  it should "continuously watch changes on a resource - deployments" in { k8s =>
    import skuber.api.client.EventType

    val deploymentOne = getNginxDeployment(deployment1, "1.7.9")
    val deploymentTwo = getNginxDeployment(deployment2, "1.7.9")

    val stream = k8s.list[DeploymentList].withTimeout().map { l =>
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
    k8s.create(deploymentOne).valueT.name shouldBe deployment1
    eventually {
      k8s.get[Deployment](deployment1).valueT.status.get.availableReplicas shouldBe 1
    }
    k8s.delete[Deployment](deployment1).valueT

    /*
     * Request times for request is defaulted to 30 seconds.
     * The idle timeout is also defaulted to 60 seconds.
     * This will ensure multiple requests are performed by
     * the source including empty responses
     */
    pause(10.seconds)

    //Create second deployment and delete it.
    k8s.create(deploymentTwo).valueT.name shouldBe deployment2
    eventually {
      k8s.get[Deployment](deployment2).valueT.status.get.availableReplicas shouldBe 1
    }
    k8s.delete[Deployment](deployment2).valueT

    eventually(timeout(30.seconds), interval(3.seconds)) {
      whenReady(k8s.get[Deployment](deployment1).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
      }

      whenReady(k8s.get[Deployment](deployment2).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
      }
    }

    // cleanup
    stream.map { killSwitch =>
      killSwitch._1.shutdown()
    }

    stream.futureValue._2.futureValue.toList.map { d =>
      (d._type, d._object.name)
    } shouldBe List((EventType.ADDED, deployment1),
      (EventType.DELETED, deployment1),
      (EventType.ADDED, deployment2),
      (EventType.DELETED, deployment2))
  }

  it should "continuously watch changes on a named resource obj from the beginning - deployment" in { k8s =>
    import skuber.api.client.EventType

    val deployment = getNginxDeployment(deployment3, "1.7.9")

    k8s.create(deployment).valueT.name shouldBe deployment3
    eventually {
      k8s.get[Deployment](deployment3).valueT.status.get.availableReplicas shouldBe 1
    }

    val stream = k8s.get[Deployment](deployment3).withTimeout().map { d =>
      k8s.watchContinuously[Deployment](d)
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

    k8s.delete[Deployment](deployment3).valueT

    eventually(timeout(30.seconds), interval(3.seconds)) {
      whenReady(k8s.get[Deployment](deployment3).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
      }
    }
    // cleanup
    stream.map { killSwitch =>
      killSwitch._1.shutdown()
    }

    val f1 = stream.futureValue

    val f2 = f1._2.futureValue

    f2.toList.map { d =>
      (d._type, d._object.name)
    } shouldBe List((EventType.ADDED, deployment3),
      (EventType.DELETED, deployment3))
  }

  it should "continuously watch changes on a named resource from the beginning - deployment" in { k8s =>
    import skuber.api.client.EventType

    val deployment = getNginxDeployment(deployment4, "1.7.9")

    k8s.create(deployment).valueT.name shouldBe deployment4
    eventually {
      k8s.get[Deployment](deployment4).valueT.status.get.availableReplicas shouldBe 1
    }

    k8s.get[Deployment](deployment4).valueT
    val stream = k8s.watchContinuously[Deployment](name = deployment4, sinceResourceVersion = None, namespace = None)
      .viaMat(KillSwitches.single)(Keep.right)
      .filter(event => event._object.name == deployment4)
      .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
      .toMat(Sink.collection)(Keep.both)
      .run()


    /*
     * Request times for request is defaulted to 30 seconds.
     * This will ensure multiple requests are performed by
     * the source including empty responses
     */
    pause(20.seconds)

    k8s.delete[Deployment](deployment4).valueT

    eventually(timeout(30.seconds), interval(3.seconds)) {
      whenReady(k8s.get[Deployment](deployment4).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
      }
    }
    // cleanup
    stream._1.shutdown()


    stream._2.futureValue.toList.map { d =>
      (d._type, d._object.name)
    } shouldBe List((EventType.ADDED, deployment4),
      (EventType.DELETED, deployment4))
  }

  it should "continuously watch changes on a named resource from a point in time - deployment (specific namespace)" in { k8s =>
    import skuber.api.client.EventType

    createNamespace(namespace5, k8s)

    val deployment = getNginxDeployment(deployment5, "1.7.9")

    k8s.create(deployment, Some(namespace5)).valueT.name shouldBe deployment5
    eventually {
      k8s.get[Deployment](deployment5, Some(namespace5)).valueT.status.get.availableReplicas shouldBe 1
    }

    val d = k8s.get[Deployment](deployment5, Some(namespace5)).valueT

    val stream = k8s.watchContinuously[Deployment](deployment5, Some(d.resourceVersion), namespace = Some(namespace5))
      .viaMat(KillSwitches.single)(Keep.right)
      .filter(event => event._object.name == deployment5)
      .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
      .toMat(Sink.collection)(Keep.both)
      .run()


    /*
     * Request times for request is defaulted to 30 seconds.
     * The idle timeout is also defaulted to 60 seconds.
     * This will ensure multiple requests are performed by
     * the source including empty responses
     */
    pause(20.seconds)

    k8s.delete[Deployment](deployment5, namespace = Some(namespace5)).valueT

    eventually(timeout(30.seconds), interval(3.seconds)) {
      whenReady(k8s.get[Deployment](deployment5, namespace = Some(namespace5)).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
      }
    }

    // cleanup
    stream._1.shutdown()

    stream._2.futureValue.toList.map { d =>
      (d._type, d._object.name)
    } shouldBe List((EventType.DELETED, deployment5))
  }

  def pause(length: Duration): Unit = {
    Thread.sleep(length.toMillis)
  }

}
