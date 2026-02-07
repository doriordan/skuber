package skuber.operator.crd

import akka.stream._
import akka.stream.scaladsl._
import skuber.api.client.{EventType, WatchEvent}
import skuber.model.ListResource

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
 * Akka-based integration test for Autoscaler.
 */
class AkkaAutoscalerSpec extends AutoscalerSpec with AkkaK8SFixture {

  import Autoscaler.given

  it should "watch Autoscaler resources" in {
    val watchTestResourceName = s"watch-test-${java.util.UUID.randomUUID().toString.take(8)}"
    val testResource = Autoscaler(watchTestResourceName, Autoscaler.Spec(1, "nginx:watch"))

    val trackedEvents = ListBuffer.empty[WatchEvent[Autoscaler.Resource]]
    val trackEvents: Sink[WatchEvent[Autoscaler.Resource], ?] = Sink.foreach { event =>
      System.out.println(s"Got event: ${event}")
      trackedEvents += event
    }

    withAkkaK8sClient({ k8s =>
      def getCurrentResourceVersion: Future[String] = k8s.list[ListResource[Autoscaler.Resource]]().map { l =>
        l.resourceVersion
      }

      def watchAndTrackEvents(sinceVersion: String) = {
        k8s
          .getWatcher[Autoscaler]
          .watchStartingFromVersion(sinceVersion)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(trackEvents)(Keep.both).run()
      }

      val killSwitchFut: Future[UniqueKillSwitch] = for {
        currentResourceVersion <- getCurrentResourceVersion
        (kill, _) = watchAndTrackEvents(currentResourceVersion)
        created <- k8s.create(testResource)
        _ <- k8s.update(testResource.copy(metadata = created.metadata, status = Some(Autoscaler.Status(2, true))))
        _ = Thread.sleep(10000)
        _ <- k8s.delete[Autoscaler.Resource](watchTestResourceName)
      } yield kill

      Await.ready(killSwitchFut, 60.seconds)

      eventually(timeout(30.seconds), interval(1.second)) {
        trackedEvents.size shouldBe 2
        trackedEvents.head._type shouldBe EventType.ADDED
        trackedEvents.head._object.name shouldBe watchTestResourceName
        trackedEvents.head._object.spec.desiredReplicas shouldBe 1
        trackedEvents(1)._type shouldBe EventType.DELETED
      }

      // cleanup
      killSwitchFut.map { killSwitch =>
        killSwitch.shutdown()
        succeed
      }
    }, 120.seconds)
  }
}
