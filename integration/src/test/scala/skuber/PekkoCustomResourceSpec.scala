package skuber

import org.apache.pekko
import org.apache.pekko.stream.*
import org.apache.pekko.stream.scaladsl.*
import skuber.api.client.K8SException
import skuber.model.apiextensions.v1.CustomResourceDefinition

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import scala.util.{Success, Failure}


/**
  * Extend base custom
  * @author David O'Riordan
  */
class PekkoCustomResourceSpec extends CustomResourceSpec with PekkoK8SFixture {

    it should "recreate the CRD" in {
      withK8sClient { k8s =>
        k8s.create(TestResource.crd) map { c =>
          assert(c.name == TestResource.crd.name)
          assert(c.spec.defaultVersion == "v1alpha1")
          assert(c.spec.group == Some(("test.skuber.io")))
        }
      }
    }
    
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
        
        def createTestResource()= k8s.create(testResource)
        def deleteTestResource()= k8s.delete[TestResource](testResourceName)

        val killSwitchFut: Future[UniqueKillSwitch] = for {
          currentTestResourceVersion <- getCurrentResourceVersion
          (kill, _) = watchAndTrackEvents(currentTestResourceVersion)
          _  <- createTestResource()
          _ <- deleteTestResource()
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
    
    it should "cleanup the CRD" in {
      withK8sClient { k8s =>
        k8s.delete[CustomResourceDefinition](TestResource.crd.name)
        eventually(timeout(200.seconds), interval(3.seconds)) {
          val retrieveCrd = k8s.get[CustomResourceDefinition](TestResource.crd.name)
          val crdRetrieved = Await.ready(retrieveCrd, 2.seconds).value.get
          crdRetrieved match {
            case s: Success[_] => fail("Deleted CRD still exists")
            case Failure(ex) => ex match {
              case ex: K8SException if ex.status.code.contains(404) => succeed
              case _ => fail(s"Unexpected exception: ${ex.getMessage}")
            }
          }
        }
      }
    }
}
