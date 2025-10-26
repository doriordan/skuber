package skuber

import akka.stream._
import akka.stream.scaladsl._
import skuber.akkaclient.AkkaKubernetesClient
import skuber.model.apiextensions.v1.CustomResourceDefinition

import scala.concurrent.Future
import scala.concurrent.duration._

class AkkaCustomResourceSpec extends CustomResourceSpec with AkkaK8SFixture  {

    it should "watch the custom resources" in { k8s =>
      import skuber.api.client.{EventType, WatchEvent}

      import scala.collection.mutable.ListBuffer

      val testResourceName=java.util.UUID.randomUUID().toString
      val testResource = TestResource(testResourceName, TestResource.Spec(1))

      val trackedEvents = ListBuffer.empty[WatchEvent[TestResource]]
      val trackEvents: Sink[WatchEvent[TestResource],_] = Sink.foreach { event =>
        trackedEvents += event
      }

      def getCurrentResourceVersion: Future[String] = k8s.list[TestResourceList]().map { l =>
        l.resourceVersion
      }

      def watchAndTrackEvents(sinceVersion: String) =
      {
        val akkaK8s = k8s.asInstanceOf[AkkaKubernetesClient]
        val crEventSource = akkaK8s.getWatcher[TestResource].watchSinceVersion(sinceVersion)
        crEventSource
            .viaMat(KillSwitches.single)(Keep.right)
            .toMat(trackEvents)(Keep.both).run()
      }


      def createCRD() = k8s.create(TestResource.crd)
      def createTestResource()= k8s.create(testResource)
      def deleteTestResource()= k8s.delete[TestResource](testResourceName)
      def deleteCRD()= k8s.delete[CustomResourceDefinition](TestResource.crd.name)

      val killSwitchFut = for {
        _ <- createCRD()
        currentTestResourceVersion <- getCurrentResourceVersion
        (kill, _) = watchAndTrackEvents(currentTestResourceVersion)
        _ <- createTestResource()
        _ <- deleteTestResource()
        _ <- deleteCRD()
      } yield kill

      eventually(timeout(200.seconds), interval(3.seconds)) {
        trackedEvents.size shouldBe 2
        trackedEvents(0)._type shouldBe EventType.ADDED
        trackedEvents(0)._object.name shouldBe testResource.name
        trackedEvents(0)._object.spec shouldBe testResource.spec
        trackedEvents(1)._type shouldBe EventType.DELETED
      }

      // cleanup
      killSwitchFut.map { killSwitch =>
        killSwitch.shutdown()
        assert(condition = true)
      }
    }
}
