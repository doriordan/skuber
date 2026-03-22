package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import play.api.libs.json.*
import skuber.json.format.*
import skuber.model.ResourceSpecification.{ScaleSubresource, Schema, Subresources}
import skuber.model.apiextensions.v1.CustomResourceDefinition
import skuber.model.{CustomResource, HasStatusSubresource, ListResource, ResourceDefinition, ResourceSpecification, Scale}
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

object ZioCustomResourceSpec:

  type TestResource     = CustomResource[TestSpec, TestStatus]
  type TestResourceList = ListResource[TestResource]

  case class TestSpec(desiredReplicas: Int)
  case class TestStatus(actualReplicas: Int)

  given Format[TestSpec]   = Json.format[TestSpec]
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
  val modifiedActualReplicas  = 3

  def spec = suite("ZIO Custom Resource")(

    test("custom resource full lifecycle") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val resourceName = java.util.UUID.randomUUID().toString
        for
          createdCrd <- k8s.create(crd)
          _ = assert(createdCrd.name == crd.name)
          _ = assert(createdCrd.spec.defaultVersion == "v1alpha1")
          _ = assert(createdCrd.spec.group.contains("test.skuber.io"))
          _ <- retryUntil(
            k8s.getOption[CustomResourceDefinition](crd.name).map(_.isDefined),
            retries = 20, delay = 3.seconds, label = "CRD established"
          )
          testSpec = TestSpec(1)
          resource  = CustomResource[TestSpec, TestStatus](testSpec).withName(resourceName)
          created  <- k8s.create(resource)
          _ = assert(created.name == resourceName && created.spec == testSpec)
          got <- k8s.get[TestResource](resourceName)
          _ = assert(got.spec.desiredReplicas == 1 && got.status.isEmpty)
          currentScale <- k8s.getScale[TestResource](resourceName)
          _ <- k8s.updateScale[TestResource](resourceName, currentScale.withSpecReplicas(modifiedDesiredReplicas))
          scaled <- k8s.get[TestResource](resourceName)
          _ = assert(scaled.spec.desiredReplicas == modifiedDesiredReplicas)
          status = TestStatus(modifiedActualReplicas)
          withStatus <- k8s.updateStatus(scaled.withStatus(status))
          _ = assert(withStatus.status.contains(status))
          finalScale <- k8s.getScale[TestResource](resourceName)
          _ = assert(finalScale.spec.replicas.contains(modifiedDesiredReplicas))
          _ = assert(finalScale.status.get.replicas == modifiedActualReplicas)
          _ <- k8s.delete[TestResource](resourceName)
          _ <- retryUntilGone(k8s.get[TestResource](resourceName), retries = 40, delay = 3.seconds)
          _ <- k8s.delete[CustomResourceDefinition](crd.name)
          _ <- retryUntilGone(k8s.get[CustomResourceDefinition](crd.name), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    }

  )
