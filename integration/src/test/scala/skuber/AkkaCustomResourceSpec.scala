package skuber

import akka.stream.*
import akka.stream.scaladsl.*
import skuber.api.client.K8SException
import skuber.model.apiextensions.v1.CustomResourceDefinition

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

class AkkaCustomResourceSpec extends CustomResourceSpec with AkkaK8SFixture  {

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
      import skuber.api.client.{EventType, WatchEvent}

      import scala.collection.mutable.ListBuffer

      val testResourceName=java.util.UUID.randomUUID().toString
      val testResource = TestResource(testResourceName, TestResource.Spec(1))

      val trackedEvents = ListBuffer.empty[WatchEvent[TestResource]]
      val trackEvents: Sink[WatchEvent[TestResource],_] = Sink.foreach { event =>
        trackedEvents += event
      }

      withAkkaK8sClient ({ k8s =>

        def getCurrentResourceVersion: Future[String] = k8s.list[TestResourceList]().map { l =>
          l.resourceVersion
        }

        def watchAndTrackEvents(sinceVersion: String) = {
          val crEventSource = k8s.getWatcher[TestResource].watchStartingFromVersion(sinceVersion)
          crEventSource
              .viaMat(KillSwitches.single)(Keep.right)
              .toMat(trackEvents)(Keep.both).run()
        }


        def createTestResource() = k8s.create(testResource)

        def deleteTestResource() = k8s.delete[TestResource](testResourceName)

        val killSwitchFut = for {
          currentTestResourceVersion <- getCurrentResourceVersion
          (kill, _) = watchAndTrackEvents(currentTestResourceVersion)
          _ <- createTestResource()
          _ <- deleteTestResource()
        } yield kill

        Await.ready(killSwitchFut, 60.seconds)

        eventually(timeout(10.seconds), interval(3.seconds)) {
          trackedEvents.size shouldBe 2
          trackedEvents(0)._type shouldBe EventType.ADDED
          trackedEvents(0)._object.name shouldBe testResource.name
          trackedEvents(0)._object.spec shouldBe testResource.spec
          trackedEvents(1)._type shouldBe EventType.DELETED
        }

        // cleanup
        killSwitchFut.map { killSwitch =>
          killSwitch.shutdown()
          succeed
        }
      },300.seconds)
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
