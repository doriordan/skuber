package skuber.operator.crd

import scala.annotation.experimental
import play.api.libs.json.{Format, Json}
import skuber.model.*

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
  statusSubresource = "false"
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
