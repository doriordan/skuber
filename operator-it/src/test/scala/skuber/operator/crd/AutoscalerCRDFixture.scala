package skuber.operator.crd

import play.api.libs.json.Json
import skuber.model.ObjectMeta
import skuber.model.ResourceSpecification
import skuber.model.ResourceSpecification.{Names, Schema, Scope}
import skuber.model.NonCoreResourceSpecification
import skuber.model.apiextensions.v1.CustomResourceDefinition

/**
  * Test fixture for creating the autoscsler CRD that needs to be setup on the cluster for the tests to run.
  */
object AutoscalerCRDFixture:

  val crdName = "autoscalers.test.skuber.io"

  val schema: Schema = Schema(Json.obj(
    "type" -> "object",
    "properties" -> Json.obj(
      "spec" -> Json.obj(
        "type" -> "object",
        "properties" -> Json.obj(
          "desiredReplicas" -> Json.obj("type" -> "integer"),
          "image" -> Json.obj("type" -> "string")
        ),
        "required" -> Json.arr("desiredReplicas", "image")
      ),
      "status" -> Json.obj(
        "type" -> "object",
        "properties" -> Json.obj(
          "availableReplicas" -> Json.obj("type" -> "integer"),
          "ready" -> Json.obj("type" -> "boolean")
        )
      )
    )
  ))

  val crd: CustomResourceDefinition = CustomResourceDefinition(
    metadata = ObjectMeta(name = crdName),
    spec = NonCoreResourceSpecification(
      apiGroup = "test.skuber.io",
      version = Some("v1"),
      scope = Scope.Namespaced,
      names = Names(
        plural = "autoscalers",
        singular = "autoscaler",
        kind = "Autoscaler",
        shortNames = List("as")
      ),
      versions = Some(List(
        ResourceSpecification.Version(
          name = "v1",
          served = true,
          storage = true,
          schema = Some(schema),
          subresources = Some(ResourceSpecification.Subresources(
            status = Some(ResourceSpecification.StatusSubresource())
          ))
        )
      )),
      subresources = None
    )
  )
