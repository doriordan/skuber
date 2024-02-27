package skuber

import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import org.scalatest.BeforeAndAfterAll
import skuber.apiextensions.v1.CustomResourceDefinition
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.libs.json._
import skuber.FutureUtil.FutureOps
import skuber.ResourceSpecification.{ScaleSubresource, Schema, Subresources}

import java.util.UUID.randomUUID
import scala.concurrent.duration._

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
 * This tests the full GA (v1) version of CRDs, so can only be run against versions 1.19 or later of Kubernetes.
 *
 * @author David O'Riordan
 */
class CustomResourceV1Spec extends K8SFixture with Eventually with Matchers with ScalaFutures with BeforeAndAfterAll with TestRetry {

  // Convenient aliases for the custom object and list resource types to be passed to the skuber API methods
  type TestResource = CustomResource[TestResource.Spec, TestResource.Status]
  type TestResourceList = ListResource[TestResource]

  override def beforeAll(): Unit = {
    val k8s = k8sInit(config)
    k8s.create(TestResource.crd).valueT
  }

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = k8s.delete[CustomResourceDefinition](TestResource.crd.name).withTimeout().recover { case _ => () }
    results.futureValue

    k8s.close
    system.terminate().recover { case _ => () }.valueT
  }

  object TestResource {

    /*
     * Define the CustomResource model (spec and status), the implicit values that need to be passed to the
     * skuber API to enable it to send and receive TestResource/TestResourceList types, and the matching CRD
    */

    // TestResource model
    case class Spec(desiredReplicas: Int)

    case class Status(actualReplicas: Int)

    /*
     * Define the versions information for the CRD, including schema - see
     * https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definition-versioning/
     * At least one version is required since CRD v1, but only needed by skuber if creating or updating the CRD itself - this is
     * the case in this test, however main expected use cases for skuber are operators manipulating custom resources but not the CRDs
     * themselves - in which case specifying version information here is unnecessary.
     */
    private def getVersions(): List[ResourceSpecification.Version] = {

      // A CRD schema is just represented as a generic Play Json object in the skuber model, as it will
      // rarely need to be specified by clients defining a full typed model for json schemas is deemed overkill.
      val jsonSchema = JsObject(Map(
        "type" -> JsString("object"),
        "properties" -> JsObject(Map(
          "spec" -> JsObject(Map(
            "type" -> JsString("object"),
            "properties" -> JsObject(Map(
              "desiredReplicas" -> JsObject(Map(
                "type" -> JsString("integer")
              ))
            ))
          )),
          "status" -> JsObject(Map(
            "type" -> JsString("object"),
            "properties" -> JsObject(Map(
              "actualReplicas" -> JsObject(Map(
                "type" -> JsString("integer")
              ))
            ))
          ))
        ))
      ))

      List(
        ResourceSpecification.Version(
          "v1alpha1",
          served = true,
          storage = true,
          schema = Some(Schema(jsonSchema)), // schema is required since v1
          subresources = Some(Subresources()
            .withStatusSubresource() // enable status subresource
            .withScaleSubresource(ScaleSubresource(".spec.desiredReplicas", ".status.actualReplicas")) // enable scale subresource
          )
        )
      )
    }

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
    implicit val testResourceDefinition: ResourceDefinition[CustomResource[TestResource.Spec, TestResource.Status]] = ResourceDefinition[TestResource](
      group = "test.skuber.io",
      version = "v1alpha1",
      kind = "SkuberTestV1",
      shortNames = List("testv1", "testv1s"), // not needed, but handy if debugging the tests
      versions = getVersions() // only needed for creating or updating the CRD, not needed if just manipulating custom resources
    )

    // the following implicit values enable the scale and status methods on the skuber API to be called for this type
    // (these calls will be rejected unless the subresources are enabled on the CRD)
    implicit val statusSubEnabled: HasStatusSubresource[TestResource] = CustomResource.statusMethodsEnabler[TestResource]
    implicit val scaleSubEnabled: Scale.SubresourceSpec[TestResource] = CustomResource.scalingMethodsEnabler[TestResource]

    // Construct an exportable Kubernetes CRD that mirrors the details in the matching implicit resource definition above -
    // the test will create it on Kubernetes so that the subsequent test requests can be handled by the cluster
    val crd = CustomResourceDefinition[TestResource](testResourceDefinition)

    // Convenience method for constructing custom resources of the required type from a name snd a spec
    def apply(name: String, spec: Spec): CustomResource[TestResource.Spec, TestResource.Status] = CustomResource[TestResource.Spec, TestResource.Status](spec)(testResourceDefinition).withName(name)
  }

  val initialDesiredReplicas = 1

  val modifiedDesiredReplicas = 2
  val modifiedActualReplicas = 3

  behavior.of("CustomResource")

  it should "get a crd" in { k8s =>
    val getResource = k8s.get[CustomResourceDefinition](TestResource.crd.name).valueT
    assert(getResource.name == TestResource.crd.name)
  }

  it should "create a new custom resource defined by the crd" in { k8s =>
    val testResourceName: String = randomUUID().toString
    val testResourceCreated = createNamedTestResource(k8s = k8s, name = testResourceName, replicas = 1)

    val expectedSpec = TestResource.Spec(1)
    assert(testResourceCreated.name == testResourceName)
    assert(testResourceCreated.spec == expectedSpec)

    val resourceGet = k8s.get[TestResource](testResourceName).valueT

    assert(resourceGet.name == testResourceName)
    assert(resourceGet.spec.desiredReplicas == 1)
    assert(resourceGet.status.isEmpty)

  }

  it should "get the custom resource" in { k8s =>
    val testResourceName: String = randomUUID().toString
    createNamedTestResource(k8s = k8s, name = testResourceName, replicas = 1)

    k8s.get[TestResource](testResourceName).map { c =>
      assert(c.name == testResourceName)
      assert(c.spec.desiredReplicas == 1)
      assert(c.status.isEmpty)
    }
  }

  it should "scale the desired replicas on the spec of the custom resource" in { k8s =>
    val modifiedDesiredReplicas = 2
    val testResourceName: String = randomUUID().toString
    createNamedTestResource(k8s = k8s, name = testResourceName, replicas = 1)

    val currentScale = k8s.getScale[TestResource](testResourceName).valueT

    k8s.updateScale[TestResource](testResourceName, currentScale.withSpecReplicas(modifiedDesiredReplicas)).valueT
    val updated = k8s.get[TestResource](testResourceName).valueT
    assert(updated.spec.desiredReplicas == modifiedDesiredReplicas)
  }

  it should "update the status on the custom resource with a modified actual replicas count" in { k8s =>
    val specReplicas = 1
    val testResourceName: String = randomUUID().toString
    createNamedTestResource(k8s = k8s, name = testResourceName, replicas = specReplicas)

    val modifiedActualReplicas = 3
    val status = TestResource.Status(modifiedActualReplicas)

    val testResource = k8s.get[TestResource](testResourceName).valueT
    val updated = k8s.updateStatus(testResource.withStatus(status)).valueT
    updated.status shouldBe Some(status)

    val scale = k8s.getScale[TestResource](testResourceName).valueT
    scale.spec.replicas shouldBe Some(specReplicas)
    scale.status.get.replicas shouldBe modifiedActualReplicas
  }

  it should "return the modified desired and actual replica counts in response to a getScale request" in { k8s =>
    val testResourceName: String = randomUUID().toString
    createNamedTestResource(k8s = k8s, name = testResourceName, replicas = 2)

    k8s.getScale[TestResource](testResourceName).map { scale =>
      assert(scale.spec.replicas.contains(modifiedDesiredReplicas))
      assert(scale.status.get.replicas == modifiedActualReplicas)
    }
  }

  it should "delete the custom resource" in { k8s =>
    val testResourceName: String = randomUUID().toString
    createNamedTestResource(k8s = k8s, name = testResourceName, replicas = 1)
    k8s.get[TestResource](testResourceName).valueT.name shouldBe testResourceName

    k8s.delete[TestResource](testResourceName).valueT

    val failure = k8s.get[TestResource](testResourceName).withTimeout().failed.futureValue
    failure shouldBe a[K8SException]
    failure.asInstanceOf[K8SException].status.code should contain(404)
  }

  it should "getStatus on deployment - specific namespace" in { k8s =>
    val namespace = randomUUID().toString
    createNamespace(namespace, k8s)

    val testResourceName: String = randomUUID().toString
    val actualReplicas = 1
    val desiredReplicas = 7

    createNamedTestResource(k8s = k8s, name = testResourceName, replicas = desiredReplicas, namespace = Some(namespace))

    val testResource = k8s.get[TestResource](testResourceName, Some(namespace)).valueT
    k8s.updateStatus(testResource.withStatus(TestResource.Status(actualReplicas)), Some(namespace)).valueT

    val actualStatus = k8s.getStatus[TestResource](testResourceName, Some(namespace)).valueT
    actualStatus.status.map(_.actualReplicas) shouldBe Some(actualReplicas)

    deleteNamespace(namespace, k8s)
  }

  it should "watch the custom resources" in { k8s =>
    import skuber.api.client.{EventType, WatchEvent}
    import scala.collection.mutable.ListBuffer

    val testResourceName: String = randomUUID().toString
    val testResource = TestResource(testResourceName, TestResource.Spec(1))

    val trackedEvents = ListBuffer.empty[WatchEvent[TestResource]]
    val trackEvents: Sink[WatchEvent[TestResource], _] = Sink.foreach { event =>
      trackedEvents += event
    }

    def getCurrentResourceVersion: String = k8s.list[TestResourceList]().valueT.resourceVersion

    def watchAndTrackEvents(sinceVersion: String) = {
      val crEventSource = k8s.watchAll[TestResource](sinceResourceVersion = Some(sinceVersion)).valueT
      crEventSource
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(trackEvents)(Keep.both).run()
    }

    val killSwitch: UniqueKillSwitch = {
      val (kill, _) = watchAndTrackEvents(getCurrentResourceVersion)
      k8s.create(testResource).valueT
      k8s.delete[TestResource](testResourceName).valueT
      kill
    }

    eventually(timeout(30.seconds), interval(3.seconds)) {
      trackedEvents.size shouldBe 2
      trackedEvents(0)._type shouldBe EventType.ADDED
      trackedEvents(0)._object.name shouldBe testResource.name
      trackedEvents(0)._object.spec shouldBe testResource.spec
      trackedEvents(1)._type shouldBe EventType.DELETED
    }

    // cleanup
    killSwitch.shutdown()
    assert(true)
  }

  it should "delete the crd" in { k8s =>
    k8s.delete[CustomResourceDefinition](TestResource.crd.name)
    eventually(timeout(200.seconds), interval(3.seconds)) {
      val failure = k8s.get[CustomResourceDefinition](TestResource.crd.name).failed.valueT
      failure shouldBe a[K8SException]
      failure.asInstanceOf[K8SException].status.code should contain(404)
    }
  }

  def createNamedTestResource(k8s: FixtureParam, name: String, replicas: Int, namespace: Option[String] = None): CustomResource[TestResource.Spec, TestResource.Status] = {
    val testSpec = TestResource.Spec(replicas)
    k8s.create(TestResource(name, testSpec), namespace).valueT
  }
}
