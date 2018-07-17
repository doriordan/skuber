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
  * This tests making requests on custom resources based on a very simple custom resource type (TestResource) defined
  * here. (A TestResource consists of a desired replica count (spec) and corresponding actual replicas count (status))
  *
  * The tests cover the following interactions with Kubernetes via skuber:
  * - creating an appropriate Custom Resource Definition (CRD) for TestResource type
  * - retrieving the CRD
  * - creating TestResource custom resources
  * - retrieving  TestResource resources
  * - updating TestResource status (via status subresource)
  * - scaling TestResource replica count (via scale subresource)
  * - retrieval of list of TestResources
  * - watch events on TestResources
  * - deleting TestResources
  * - and finally the CRD is deleted
  *
  * Note the test requires the CustomResourceSubresources feature to be enabled (at time of writing it is an alpha feature)
  * on the Kubernetes cluster, so requires v1.10 or later of Kubernetes - on k8s v1.10 it needs a feature gate enabled,
  * but on v1.11 it is enabled by default.
  *
  * Initially tested using minikube initialised as follows:
  * 'minikube start --kubernetes-version=v1.10.0 --feature-gates=CustomResourceSubresources=true'
  *
  * @author David O'Riordan
  */
class CustomResourceSpec extends K8SFixture with Eventually with Matchers {

    val testResourceName: String = java.util.UUID.randomUUID().toString

    // Convenient aliases for the custom object and list resource types to be passed to the skuber API methods
    type TestResource=CustomResource[TestResource.Spec,TestResource.Status]
    type TestResourceList=ListResource[TestResource]

    object TestResource {

      /*
       * Define the CustomResource model (spec and status), the implicit values that need to be passed to the
       * skuber API to enable it to send and receive TestResource/TestResourceList types, and the matching CRD
      */

      // TestResource model
      case class Spec(desiredReplicas: Int)
      case class Status(actualReplicas: Int)

      // skuber requires these implicit json formatters to marshal and unmarshal the TestResource spec and status fields.
      // The CustomResource json formatter will marshal/unmarshal these to/from "spec" and "status" subobjects
      // of the overall json representation of the resource.
      implicit val specFmt: Format[Spec] = Json.format[Spec]
      implicit val statusFmt: Format[Status] = Json.format[Status]

      // Resource definition: defines the details of the API for the resource type on Kubernetes
      // Must mirror the corresponding details in the associated CRD - see Kubernetes CRD documentation.
      // This needs to be passed implicitly to the skuber API to enable it to process TestResource requests.
      // The json paths in the Scale subresource must map to the replica fields in Spec and Status
      // respectively above
      implicit val testResourceDefinition = ResourceDefinition[TestResource](
        group = "test.skuber.io",
        version = "v1alpha1",
        kind = "SkuberTest",
        shortNames = List("test","tests"),  // not needed but handy if debugging the tests
        subresources = Some(Subresources()
            .withStatusSubresource // enable status subresource
            .withScaleSubresource(ScaleSubresource(".spec.desiredReplicas",".status.actualReplicas")) // enable scale subresource
        )
      )

      // the following implicit values enable the scale and status methods on the skuber API to be called for this type
      // (these calls will be rejected unless the subresources are enabled on the CRD)
      implicit val scaleSubEnabled=CustomResource.scalingMethodsEnabler[TestResource]
      implicit val statusSubEnabled=CustomResource.statusMethodsEnabler[TestResource]

      // Construct an exportable Kubernetes CRD that mirrors the details in the matching implicit resource definition above -
      // the test will create it on Kubernetes so that the subsequent test requests can be handled by the cluster
      val crd=CustomResourceDefinition[TestResource]

      // Convenience method for constructing custom resources of the required type from a name snd a spec
      def apply(name: String, spec: Spec) = CustomResource[Spec,Status](spec).withName(name)
    }

    val initialDesiredReplicas=1

    val modifiedDesiredReplicas=2
    val modifiedActualReplicas=3

    behavior of "CustomResource"

    it should "create a crd" in { k8s =>

      k8s.create(TestResource.crd) map { c =>
        assert(c.name == TestResource.crd.name)
        assert(c.spec.defaultVersion == "v1alpha1")
        assert(c.spec.group == Some(("test.skuber.io")))
      }
    }

    it should "get the newly created crd" in { k8s =>
      k8s.get[CustomResourceDefinition](TestResource.crd.name) map { c =>
        assert(c.name == TestResource.crd.name)
      }
    }

    it should "create a new custom resource defined by the crd" in { k8s =>
      val testSpec=TestResource.Spec(1)
      val testResource=TestResource(testResourceName, testSpec)
      k8s.create(testResource).map { testResource =>
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

    it should "scale the desired replicas on the spec of the custom resource" in { k8s =>
      val updatedFut = for {
        currentScale <- k8s.getScale[TestResource](testResourceName)
        scaled <- k8s.updateScale[TestResource](testResourceName, currentScale.withSpecReplicas(modifiedDesiredReplicas))
        updated <- k8s.get[TestResource](testResourceName)
      } yield updated
      updatedFut.map { updated =>
        assert(updated.spec.desiredReplicas==modifiedDesiredReplicas)
      }
    }

    it should "update the status on the custom resource with a modified actual replicas count" in { k8s =>
      val status=TestResource.Status(modifiedActualReplicas)
      val updatedFut = for {
        testResource <- k8s.get[TestResource](testResourceName)
        updatedTestResource <- k8s.updateStatus(testResource.withStatus(status))
      } yield updatedTestResource
      updatedFut.map { updated =>
        updated.status shouldBe Some(status)
      }
    }

    it should "return the modified desired and actual replica counts in response to a getScale request" in { k8s =>
      k8s.getScale[TestResource](testResourceName).map { scale =>
        assert(scale.spec.replicas == modifiedDesiredReplicas)
        assert(scale.status.get.replicas == modifiedActualReplicas)
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
      val testResource = TestResource(testResourceName, TestResource.Spec(1))

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
      k8s.delete[CustomResourceDefinition](TestResource.crd.name)
      eventually(timeout(200 seconds), interval(3 seconds)) {
        val retrieveCrd= k8s.get[CustomResourceDefinition](TestResource.crd.name)
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
