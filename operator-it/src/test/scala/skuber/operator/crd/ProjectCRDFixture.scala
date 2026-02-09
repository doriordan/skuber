package skuber.operator.crd

import play.api.libs.json.Json
import skuber.model.ObjectMeta
import skuber.model.ResourceSpecification
import skuber.model.ResourceSpecification.{Names, Schema, Scope}
import skuber.model.NonCoreResourceSpecification
import skuber.model.apiextensions.v1.CustomResourceDefinition

/**
  * Test fixture for creating the Project CRD used by the integration tests.
  */
object ProjectCRDFixture:

  val crdName = "projects.test.skuber.io"

  val schema: Schema = Schema(Json.obj(
    "type" -> "object",
    "properties" -> Json.obj(
      "spec" -> Json.obj(
        "type" -> "object",
        "properties" -> Json.obj(
          "name" -> Json.obj("type" -> "string"),
          "owner" -> Json.obj(
            "type" -> "object",
            "properties" -> Json.obj(
              "name" -> Json.obj("type" -> "string"),
              "team" -> Json.obj("type" -> "string")
            ),
            "required" -> Json.arr("name", "team")
          ),
          "repositories" -> Json.obj(
            "type" -> "array",
            "items" -> Json.obj(
              "type" -> "object",
              "properties" -> Json.obj(
                "url" -> Json.obj("type" -> "string"),
                "language" -> Json.obj("type" -> "string")
              ),
              "required" -> Json.arr("url", "language")
            )
          ),
          "labels" -> Json.obj(
            "type" -> "object",
            "additionalProperties" -> Json.obj("type" -> "string")
          ),
          "settings" -> Json.obj(
            "type" -> "object",
            "properties" -> Json.obj(
              "retentionDays" -> Json.obj("type" -> "integer"),
              "features" -> Json.obj(
                "type" -> "array",
                "items" -> Json.obj("type" -> "string")
              ),
              "limits" -> Json.obj(
                "type" -> "object",
                "properties" -> Json.obj(
                  "cpu" -> Json.obj("type" -> "integer"),
                  "memoryMi" -> Json.obj("type" -> "integer")
                ),
                "required" -> Json.arr("cpu", "memoryMi")
              )
            ),
            "required" -> Json.arr("retentionDays", "features", "limits")
          ),
          "alerts" -> Json.obj(
            "type" -> "array",
            "items" -> Json.obj(
              "type" -> "object",
              "properties" -> Json.obj(
                "kind" -> Json.obj("type" -> "string"),
                "target" -> Json.obj("type" -> "string")
              ),
              "required" -> Json.arr("kind", "target")
            )
          )
        ),
        "required" -> Json.arr("name", "owner", "repositories", "labels", "settings")
      ),
      "status" -> Json.obj(
        "type" -> "object",
        "properties" -> Json.obj(
          "phase" -> Json.obj("type" -> "string"),
          "conditions" -> Json.obj(
            "type" -> "array",
            "items" -> Json.obj(
              "type" -> "object",
              "properties" -> Json.obj(
                "type" -> Json.obj("type" -> "string"),
                "status" -> Json.obj("type" -> "string"),
                "reason" -> Json.obj("type" -> "string")
              ),
              "required" -> Json.arr("type", "status")
            )
          ),
          "metrics" -> Json.obj(
            "type" -> "object",
            "additionalProperties" -> Json.obj("type" -> "number")
          ),
          "lastDeploy" -> Json.obj(
            "type" -> "object",
            "properties" -> Json.obj(
              "version" -> Json.obj("type" -> "string"),
              "at" -> Json.obj("type" -> "string")
            ),
            "required" -> Json.arr("version", "at")
          ),
          "members" -> Json.obj(
            "type" -> "array",
            "items" -> Json.obj(
              "type" -> "object",
              "properties" -> Json.obj(
                "name" -> Json.obj("type" -> "string"),
                "role" -> Json.obj("type" -> "string")
              ),
              "required" -> Json.arr("name", "role")
            )
          )
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
        plural = "projects",
        singular = "project",
        kind = "Project",
        shortNames = List("prj")
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
