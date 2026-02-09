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

// Project is a more complex custom resource type used to test nested structures and container formats
@experimental
@customResource(
  group = "test.skuber.io",
  version = "v1",
  kind = "Project",
  scope = Scope.Namespaced,
  statusSubresource = true
)
object Project extends CustomResourceDef[Project.Spec, Project.Status]:
  case class Owner(name: String, team: String)
  case class Repo(url: String, language: String)
  case class Limits(cpu: Int, memoryMi: Int)
  case class Settings(retentionDays: Int, features: Set[String], limits: Limits)
  case class Alert(kind: String, target: String)
  case class Spec(
    name: String,
    owner: Owner,
    repositories: List[Repo],
    labels: Map[String, String],
    settings: Settings,
    alerts: Option[List[Alert]]
  )

  case class Condition(`type`: String, status: String, reason: Option[String])
  case class DeployInfo(version: String, at: String)
  case class Member(name: String, role: String)
  case class Status(
    phase: String,
    conditions: List[Condition],
    metrics: Map[String, Double],
    lastDeploy: Option[DeployInfo],
    members: Vector[Member]
  )

type Project = Project.Resource
