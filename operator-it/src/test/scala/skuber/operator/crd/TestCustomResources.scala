package skuber.operator.crd

import scala.annotation.experimental
import play.api.libs.json.OFormat

/**
 * Custom resource definitions for integration testing.
 * These use the @customResource macro annotation to generate
 * Json serialisation, ResourceDefinition (defines the resource type to Skuber), and factory methods to
 * create new custom resources locally that can be applied to the cluster.
 *
 */

// Autoscaler is a simple custom resource type which would record desired vs actual replicas on a cluster
@experimental
@customResource(
  group = "test.skuber.io",
  version = "v1",
  kind = "Autoscaler",
  scope = Scope.Namespaced
)
object Autoscaler extends CustomResourceDef[Autoscaler.Spec, Autoscaler.Status]:
  case class Spec(desiredReplicas: Int, image: String)
  case class Status(availableReplicas: Int, ready: Boolean)

type Autoscaler = Autoscaler.Resource // this alias can be used with the API (e.g. k8s.get[Autoscaler](..)) in line with builtin types

// spec only custom resource type (no status field)
@experimental
@customResource(
  group = "test.skuber.io",
  version = "v1",
  kind = "TestConfig",
  scope = Scope.Namespaced
)
object TestConfig extends CustomResourceSpecDef[TestConfig.Spec]:
  case class Spec(data: Map[String, String])

type TestConfig = TestConfig.Resource