package skuber.operator.crd

import scala.annotation.experimental

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
  scope = Scope.Namespaced,
  statusSubresource = true
)
object Autoscaler extends CustomResourceDef[Autoscaler.Spec, Autoscaler.Status]:
  case class Spec(desiredReplicas: Int, image: String)
  case class Status(availableReplicas: Int, ready: Boolean)

// Optional alias to slightly simplify Skuber API calls (e.g. k8s.get[Autoscaler](..) instead of k8s.get[Autoscaler.Resource](..)) 
// which achieves desirable uniformity with how builtin resource kinds are used with the API e.g. k8s.get[Pod](..)
// Due to restrictions in Scala 3 macros around generating new visible types, this new type alias can't be automatically generated
type Autoscaler = Autoscaler.Resource 

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