package skuber.operator.crd

import scala.annotation.experimental
import play.api.libs.json.{JsValue, OFormat, Json}

/**
 * Test resources using the @customResource macro annotation.
 */

@experimental
@customResource(
  group = "test.example.com",
  version = "v1",
  kind = "WebApp"
)
object WebAppResource extends CustomResourceDef[WebAppResource.Spec, WebAppResource.Status]:
  case class Spec(replicas: Int, image: String, port: Int = 8080)
  case class Status(availableReplicas: Int, ready: Boolean)

@experimental
@customResource(
  group = "test.example.com",
  version = "v1alpha1",
  kind = "ConfigMap2",
  statusSubresource = false
)
object ConfigMap2Resource extends CustomResourceSpecDef[ConfigMap2Resource.Spec]:
  case class Spec(data: Map[String, String])

@experimental
@customResource(
  group = "test.example.com",
  version = "v1beta1",
  kind = "Database"
)
object DatabaseResource extends CustomResourceDef[DatabaseResource.Spec, DatabaseResource.Status]:
  case class Spec(engine: String, size: Int)
  case class Status(state: String)

@experimental
@customResource(
  group = "custom.io",
  version = "v2",
  kind = "Queue",
  plural = "queues",
  singular = "queue",
  shortNames = "q,qu",
  scope = Scope.Cluster
)
object QueueResource extends CustomResourceDef[QueueResource.Spec, QueueResource.Status]:
  case class Spec(capacity: Int, persistent: Boolean)
  case class Status(depth: Int)

/**
 * Test resource with custom formatters - user provides their own Play JSON format implementation.
 * The macro will skip generating specFormat since we define it here.
 */
@experimental
@customResource(
  group = "test.example.com",
  version = "v1",
  kind = "CustomFormat"
)
object CustomFormatResource extends CustomResourceDef[CustomFormatResource.Spec, CustomFormatResource.Status]:
  case class Spec(name: String, value: Int)
  case class Status(processed: Boolean)

  // User-provided custom formatter for Spec - macro will skip generating the override
  override protected val specFormat: OFormat[Spec] = OFormat(
    (json: JsValue) => for {
      name <- (json \ "name").validate[String]
      value <- (json \ "value").validate[Int]
    } yield Spec(name, value),
    (spec: Spec) => Json.obj("name" -> spec.name, "value" -> spec.value)
  )

  // Status uses default derivation (macro will generate statusFormat)

/**
 * Test resource with nested case classes.
 * The macro should generate formats for Address and ContactInfo before Spec.
 */
@experimental
@customResource(
  group = "test.example.com",
  version = "v1",
  kind = "Employee"
)
object EmployeeResource extends CustomResourceDef[EmployeeResource.Spec, EmployeeResource.Status]:
  case class Address(street: String, city: String, zipCode: String)
  case class ContactInfo(email: String, phone: Option[String], addresses: List[Address])
  case class Spec(name: String, department: String, contact: ContactInfo, tags: Set[String])
  case class Status(active: Boolean, lastUpdated: String)

/**
 * Test resource with deeply nested containers.
 * Tests List[Option[T]], Option[List[T]], Map[String, List[T]]
 */
@experimental
@customResource(
  group = "test.example.com",
  version = "v1",
  kind = "Project"
)
object ProjectResource extends CustomResourceDef[ProjectResource.Spec, ProjectResource.Status]:
  case class Milestone(name: String, completed: Boolean)
  case class Task(id: Int, description: String, milestone: Option[Milestone])
  case class Spec(
    name: String,
    tasks: List[Task],
    optionalTasks: List[Option[Task]],
    tasksByCategory: Map[String, List[Task]]
  )
  case class Status(completedTasks: Int, totalTasks: Int)

/**
 * Test resource with Scala 3 enums.
 */
@experimental
@customResource(
  group = "test.example.com",
  version = "v1",
  kind = "Release"
)
object ReleaseResource extends CustomResourceDef[ReleaseResource.Spec, ReleaseResource.Status]:
  enum Phase:
    case Planning, Active, Done

  enum Risk:
    case Low, Medium, High

  case class Spec(name: String, phase: Phase, risks: List[Risk])
  case class Status(phase: Phase, lastRisk: Option[Risk])
