package skuber

import akka.stream._
import akka.stream.scaladsl._
import skuber.apiextensions.CustomResourceDefinition
import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import play.api.libs.json._
import skuber.ResourceSpecification.{Subresources,ScaleSubresource}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
  * @author David O'Riordan
  */
class CustomResourceSpec extends K8SFixture with Eventually with Matchers {

    val testResourceName: String = java.util.UUID.randomUUID().toString

    case class TestSpec(desiredReplicas: Int)
    case class TestStatus(actualReplicas: Int)

    implicit val specFmt:Format[TestSpec] =Json.format[TestSpec]
    implicit val statusFmt:Format[TestStatus] =Json.format[TestStatus]

    type TestResource=CustomResource[TestSpec,TestStatus]
    type TestResourceList=ListResource[TestResource]


    implicit val testResourceDefinition = ResourceDefinition[TestResource](
      group = "test.skuber.io",
      version = "v1alpha1",
      kind = "SkuberTest",
      shortNames = List("test","tests"),
      subresources = Some(Subresources()
        .withStatusSubresource
        .withScaleSubresource(ScaleSubresource(".spec.desiredReplicas",".status.actualReplicas"))
      )
    )

    val testCrd=CustomResourceDefinition[TestResource]

    println(Json.toJson(testCrd))

    behavior of "CustomResource"

    it should "create a crd" in { k8s =>

      k8s.create(testCrd) map { c =>
        assert(c.name == testCrd.name)
        assert(c.spec.defaultVersion == "v1alpha1")
        assert(c.spec.group == Some(("test.skuber.io")))
      }
    }

    it should "get the newly created crd" in { k8s =>
      k8s.get[CustomResourceDefinition](testCrd.name) map { c =>
        assert(c.name == testCrd.name)
      }
    }

    it should "create a new custom resource defined by the crd" in { k8s =>
      val testSpec=TestSpec(1)
      val cr=CustomResource(testSpec).withName(testResourceName)
      k8s.create(cr).map { testResource =>
        assert(testResource.name==testResourceName)
        assert(testResource.spec==testSpec)
      }
    }

    it should "get the custom resource" in { k8s =>
      k8s.get[TestResource](testResourceName).map { c =>
        assert(c.name == testResourceName)
        assert(c.spec.desiredReplicas == 1)
        assert(c.status == None)
      }
    }

    // Note: the following requires Custom Resources subresources to be enabled - on v1.10 this requires a feature gate
    // to be enabled, from v1.11 it s enabled by default
    it should "update the status on the custom resource" in { k8s =>
      val status=TestStatus(1)
      val updatedFut = for {
        testResource <- k8s.get[TestResource](testResourceName)
        updatedTestResource <- k8s.updateStatus(testResource.withStatus(status))
      } yield updatedTestResource
      updatedFut.map { updated =>
        updated.status shouldBe Some(status)
      }
    }

    it should "delete the custom resource" in { k8s =>
      k8s.delete[TestResource](testResourceName)
      eventually(timeout(200 seconds), interval(3 seconds)) {
        val retrieveCr= k8s.get[TestResource](testResourceName)
        val crRetrieved=Await.ready(retrieveCr, 2 seconds).value.get
        crRetrieved match {
          case s: Success[_] => assert(false)
          case Failure(ex) => ex match {
            case ex: K8SException if ex.status.code.contains(404) => assert(true)
            case _ => assert(false)
          }
        }
      }
    }

    it should "watch the custom resources" in { k8s =>
      import skuber.api.client.{EventType, WatchEvent}
      import scala.collection.mutable.ListBuffer

      val testResourceName=java.util.UUID.randomUUID().toString
      val testResource = CustomResource(TestSpec(1)).withName(testResourceName)

      val trackedEvents = ListBuffer.empty[WatchEvent[TestResource]]
      val trackEvents: Sink[WatchEvent[TestResource],_] = Sink.foreach { event =>
        trackedEvents += event
      }

      def getCurrentResourceVersion: Future[String] = k8s.list[TestResourceList].map { l =>
        l.resourceVersion
      }
      def watchAndTrackEvents(sinceVersion: String) =
      {
        k8s.watchAll[TestResource](sinceResourceVersion = Some(sinceVersion)).map { crEventSource =>
          crEventSource
            .viaMat(KillSwitches.single)(Keep.right)
            .toMat(trackEvents)(Keep.both).run()
        }
      }
      def createTestResource= k8s.create(testResource)
      def deleteTestResource= k8s.delete[TestResource](testResourceName)

      val killSwitchFut = for {
        currentTestResourceVersion <- getCurrentResourceVersion
        (kill, _) <- watchAndTrackEvents(currentTestResourceVersion)
        testResource <- createTestResource
        deleted <- deleteTestResource
      } yield kill

      eventually(timeout(200 seconds), interval(3 seconds)) {
        trackedEvents.size shouldBe 2
        trackedEvents(0)._type shouldBe EventType.ADDED
        trackedEvents(0)._object.name shouldBe testResource.name
        trackedEvents(0)._object.spec shouldBe testResource.spec
        trackedEvents(1)._type shouldBe EventType.DELETED
      }

      // cleanup
      killSwitchFut.map { killSwitch =>
        killSwitch.shutdown()
        assert(true)
      }
    }

    it should "delete the crd" in { k8s =>
      k8s.delete[CustomResourceDefinition](testCrd.name)
      eventually(timeout(200 seconds), interval(3 seconds)) {
        val retrieveCrd= k8s.get[CustomResourceDefinition](testCrd.name)
        val crdRetrieved=Await.ready(retrieveCrd, 2 seconds).value.get
        crdRetrieved match {
          case s: Success[_] => assert(false)
          case Failure(ex) => ex match {
            case ex: K8SException if ex.status.code.contains(404) => assert(true)
            case _ => assert(false)
          }
        }
      }
    }
}
