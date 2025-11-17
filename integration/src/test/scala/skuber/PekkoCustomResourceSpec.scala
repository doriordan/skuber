package skuber

import org.apache.pekko
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import skuber.model.apiextensions.v1.CustomResourceDefinition
import skuber.pekkoclient.PekkoKubernetesClient

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._


/**
  * Extend base custom
  * @author David O'Riordan
  */
class PekkoCustomResourceSpec extends CustomResourceSpec with PekkoK8SFixture {

    it should "watch the custom resources" in {

      import TestResource.testResourceDefinition
      import skuber.api.client.{EventType, WatchEvent}

      import scala.collection.mutable.ListBuffer

      val testResourceName=java.util.UUID.randomUUID().toString
      val testResource = TestResource(testResourceName, TestResource.Spec(1))

      val trackedEvents = ListBuffer.empty[WatchEvent[TestResource]]
      val trackEvents: Sink[WatchEvent[TestResource],_] = Sink.foreach { event =>
        trackedEvents += event
      }

      withPekkoK8sClient ({ k8s =>
        def getCurrentResourceVersion: Future[String] = k8s.list[TestResourceList]().map { l =>
          l.resourceVersion
        }

        def watchAndTrackEvents(sinceVersion: String) =
        {
          val s: Source[WatchEvent[TestResource], _] = k8s.getWatcher[TestResource].watch()

          k8s
            .getWatcher[TestResource]
            .watchStartingFromVersion(sinceVersion)
            .viaMat(KillSwitches.single)(Keep.right)
            .toMat(trackEvents)(Keep.both).run()
        }

        def createCRD() = k8s.create(TestResource.crd)
        def createTestResource()= k8s.create(testResource)
        def deleteTestResource()= k8s.delete[TestResource](testResourceName)
        def deleteCRD()= k8s.delete[CustomResourceDefinition](TestResource.crd.name)

        val killSwitchFut: Future[UniqueKillSwitch] = for {
          _ <- createCRD()
          currentTestResourceVersion <- getCurrentResourceVersion
          (kill, _) = watchAndTrackEvents(currentTestResourceVersion)
          _  <- createTestResource()
          _ <- deleteTestResource()
          _ <- deleteCRD()
        } yield kill

        Await.ready(killSwitchFut, 60.seconds)

        eventually(timeout(200.seconds), interval(3.seconds)) {
          trackedEvents.size shouldBe 2
          trackedEvents.head._type shouldBe EventType.ADDED
          trackedEvents.head._object.name should be(testResource.name)
          assert(trackedEvents.head._object.spec.desiredReplicas == testResource.spec.desiredReplicas)
          trackedEvents(1)._type shouldBe EventType.DELETED
        }

        // cleanup
        killSwitchFut.map { killSwitch =>
          killSwitch.shutdown()
          succeed
        }
      }, 300.seconds)
    }
}
