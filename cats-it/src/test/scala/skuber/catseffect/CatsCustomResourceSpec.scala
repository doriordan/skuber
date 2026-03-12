package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import play.api.libs.json.*
import skuber.api.client.{LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.ResourceSpecification.{ScaleSubresource, Schema, Subresources}
import skuber.model.apiextensions.v1.CustomResourceDefinition
import skuber.model.{CustomResource, HasStatusSubresource, ListResource, ResourceDefinition, ResourceSpecification, Scale}
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*

class CatsCustomResourceSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 10.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  // Custom resource type definitions
  type TestResource = CustomResource[TestSpec, TestStatus]
  type TestResourceList = ListResource[TestResource]

  case class TestSpec(desiredReplicas: Int)
  case class TestStatus(actualReplicas: Int)

  given Format[TestSpec] = Json.format[TestSpec]
  given Format[TestStatus] = Json.format[TestStatus]

  private def getVersions: List[ResourceSpecification.Version] =
    val jsonSchema = JsObject(Map(
      "type" -> JsString("object"),
      "properties" -> JsObject(Map(
        "spec" -> JsObject(Map("type" -> JsString("object"),
          "properties" -> JsObject(Map("desiredReplicas" -> JsObject(Map("type" -> JsString("integer"))))))),
        "status" -> JsObject(Map("type" -> JsString("object"),
          "properties" -> JsObject(Map("actualReplicas" -> JsObject(Map("type" -> JsString("integer")))))))
      ))
    ))
    List(ResourceSpecification.Version("v1alpha1", served = true, storage = true,
      schema = Some(Schema(jsonSchema)),
      subresources = Some(Subresources()
        .withStatusSubresource()
        .withScaleSubresource(ScaleSubresource(".spec.desiredReplicas", ".status.actualReplicas")))))

  given testResourceDef: ResourceDefinition[TestResource] = ResourceDefinition[TestResource](
    group = "test.skuber.io", version = "v1alpha1", kind = "SkuberTest",
    shortNames = List("test", "tests"), versions = getVersions)

  given HasStatusSubresource[TestResource] = CustomResource.statusMethodsEnabler[TestResource]
  given Scale.SubresourceSpec[TestResource] = CustomResource.scalingMethodsEnabler[TestResource]

  val crd = CustomResourceDefinition[TestResource]

  val modifiedDesiredReplicas = 2
  val modifiedActualReplicas = 3

  client.test("custom resource full lifecycle") { k8s =>
    val resourceName = java.util.UUID.randomUUID().toString
    for
      // Create and verify CRD
      createdCrd <- k8s.create(crd).map(_.getOrElse(fail("Create CRD failed")))
      _ = assertEquals(createdCrd.name, crd.name)
      _ = assertEquals(createdCrd.spec.defaultVersion, "v1alpha1")
      _ = assertEquals(createdCrd.spec.group, Some("test.skuber.io"))
      // Wait for CRD to be established
      _ <- retryUntil(
        k8s.get[CustomResourceDefinition](crd.name).map(_.isRight),
        retries = 20, delay = 3.seconds, label = "CRD established"
      )
      // Create custom resource
      testSpec = TestSpec(1)
      resource = CustomResource[TestSpec, TestStatus](testSpec).withName(resourceName)
      created <- k8s.create(resource).map(_.getOrElse(fail("Create CR failed")))
      _ = assertEquals(created.name, resourceName)
      _ = assertEquals(created.spec, testSpec)
      // Get it
      got <- k8s.get[TestResource](resourceName).map(_.getOrElse(fail("Get CR failed")))
      _ = assertEquals(got.name, resourceName)
      _ = assertEquals(got.spec.desiredReplicas, 1)
      _ = assert(got.status.isEmpty)
      // Scale
      currentScale <- k8s.getScale[TestResource](resourceName).map(_.getOrElse(fail("GetScale failed")))
      _ <- k8s.updateScale[TestResource](resourceName, currentScale.withSpecReplicas(modifiedDesiredReplicas))
      scaled <- k8s.get[TestResource](resourceName).map(_.getOrElse(fail("Get after scale failed")))
      _ = assertEquals(scaled.spec.desiredReplicas, modifiedDesiredReplicas)
      // Update status
      status = TestStatus(modifiedActualReplicas)
      withStatus <- k8s.updateStatus(scaled.withStatus(status)).map(_.getOrElse(fail("UpdateStatus failed")))
      _ = assertEquals(withStatus.status, Some(status))
      // Verify scale reflects both
      finalScale <- k8s.getScale[TestResource](resourceName).map(_.getOrElse(fail("Final GetScale failed")))
      _ = assert(finalScale.spec.replicas.contains(modifiedDesiredReplicas))
      _ = assertEquals(finalScale.status.get.replicas, modifiedActualReplicas)
      // Delete CR
      _ <- k8s.delete[TestResource](resourceName)
      _ <- retryUntilGone(k8s.get[TestResource](resourceName), retries = 40, delay = 3.seconds)
      // Delete CRD
      _ <- k8s.delete[CustomResourceDefinition](crd.name)
      _ <- retryUntilGone(k8s.get[CustomResourceDefinition](crd.name), retries = 40, delay = 3.seconds)
    yield ()
  }
