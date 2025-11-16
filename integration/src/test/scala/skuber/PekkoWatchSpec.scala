package skuber

import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import skuber.model.apps.v1.{Deployment, DeploymentList}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.{postfixOps, reflectiveCalls}

class PekkoWatchSpec extends WatchSpec with PekkoK8SFixture {

  it should "continuously watch changes on a resource kind from a point in time - deployments" in {
    withPekkoK8sClient ({ k8s =>
      import skuber.api.client.EventType

      val deploymentOneName = java.util.UUID.randomUUID().toString
      val deploymentTwoName = java.util.UUID.randomUUID().toString
      val deploymentOne = getNginxDeploymentForWatch(deploymentOneName)
      val deploymentTwo = getNginxDeploymentForWatch(deploymentTwoName)

      val stream = k8s.list[DeploymentList]().map(_.resourceVersion).map { currentResourceVersion =>
        val eventSource = k8s.getWatcher[Deployment].watchStartingFromVersion(currentResourceVersion)
        eventSource
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
    }, 300.seconds)
  }

  it should "continuously watch changes at cluster wide scope from a point in time" in {
    withPekkoK8sClient ({ k8s =>
      import skuber.api.client.EventType

      val deploymentOneName = java.util.UUID.randomUUID().toString
      val deploymentTwoName = java.util.UUID.randomUUID().toString
      val deploymentOne = getNginxDeploymentForWatch(deploymentOneName)
      val deploymentTwo = getNginxDeploymentForWatch(deploymentTwoName)

      val stream = k8s.list[DeploymentList]().map { l =>
        val eventSource = k8s.getWatcher[Deployment].watchClusterStartingFromVersion(l.resourceVersion)
        eventSource
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
    }, 300.seconds)
  }

  it should "continuously watch changes on a named resource obj in current namespace from any resource version - deployment" in {
    withPekkoK8sClient ({ k8s =>
      import skuber.api.client.EventType

      val deploymentName = java.util.UUID.randomUUID().toString
      val deployment = getNginxDeploymentForWatch(deploymentName)

      k8s.create(deployment).futureValue.name shouldBe deploymentName
      eventually {
        k8s.get[Deployment](deploymentName).futureValue.status.get.availableReplicas shouldBe 1
      }

      val stream = k8s.getWatcher[Deployment].watchObject(deploymentName)
          .viaMat(KillSwitches.single)(Keep.right)
          .filter(event => event._object.name == deploymentName)
          .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
          .toMat(Sink.collection)(Keep.both)
          .run()

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
      stream._1.shutdown()

      val f2 = stream._2.futureValue

      f2.toList.map { d =>
        (d._type, d._object.name)
      } shouldBe List(
        (EventType.ADDED, deploymentName),
        (EventType.DELETED, deploymentName)
      )
    }, 300.seconds)
  }

  it should "continuously watch changes on a named resource from a point in time - deployment" in {
    withPekkoK8sClient ({ k8s =>
      import skuber.api.client.EventType

      val deploymentName = java.util.UUID.randomUUID().toString
      val deployment = getNginxDeploymentForWatch(deploymentName)

      k8s.create(deployment).futureValue.name shouldBe deploymentName
      eventually {
        k8s.get[Deployment](deploymentName).futureValue.status.get.availableReplicas shouldBe 1
      }

      val stream = k8s.get[Deployment](deploymentName).map { d =>
        k8s.getWatcher[Deployment].watchObjectStartingFromVersion(deploymentName, d.resourceVersion)
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
    }, 300.seconds)
  }

  def pause(length: Duration): Unit ={
    Thread.sleep(length.toMillis)
  }
}
