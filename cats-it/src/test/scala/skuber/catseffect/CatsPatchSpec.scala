package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import skuber.api.client.{LoggingContext, RequestLoggingContext}
import skuber.api.patch.*
import skuber.json.format.*
import skuber.model.{ObjectMeta, Pod}
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*

class CatsPatchSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 10.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  client.test("patch pod with multiple strategies") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    val pod = Pod(
      metadata = ObjectMeta(name = name, labels = Map("label" -> "1"), annotations = Map("annotation" -> "1")),
      spec = Some(Pod.Spec(containers = List(getNginxContainer())))
    )
    for
      _ <- k8s.create(pod).map(_.getOrElse(fail("Create failed")))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.status.exists(_.phase.contains(Pod.Phase.Running)))),
        retries = 30, delay = 3.seconds, label = "pod running"
      )
      // Strategic merge patch (default)
      r1 = java.util.UUID.randomUUID().toString
      _ <- k8s.patch[MetadataPatch, Pod](name, MetadataPatch(labels = Some(Map("foo" -> r1)), annotations = None))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.metadata.labels.get("foo").contains(r1))),
        retries = 20, delay = 1.second, label = "strategic merge patch applied"
      )
      // Explicit strategic merge
      r2 = java.util.UUID.randomUUID().toString
      _ <- k8s.patch[MetadataPatch, Pod](name, MetadataPatch(labels = Some(Map("foo" -> r2)), annotations = None, strategy = StrategicMergePatchStrategy))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.metadata.labels.get("foo").contains(r2))),
        retries = 20, delay = 1.second, label = "explicit strategic merge applied"
      )
      // Json merge patch
      r3 = java.util.UUID.randomUUID().toString
      _ <- k8s.patch[MetadataPatch, Pod](name, MetadataPatch(labels = Some(Map("foo" -> r3)), annotations = None, strategy = JsonMergePatchStrategy))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.metadata.labels.get("foo").contains(r3))),
        retries = 20, delay = 1.second, label = "json merge patch applied"
      )
      // Json patch
      r4 = java.util.UUID.randomUUID().toString
      _ <- k8s.patch[JsonPatch, Pod](name, JsonPatch(List(
        JsonPatchOperation.Add("/metadata/labels/foo", r4)
      )))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.metadata.labels.get("foo").contains(r4))),
        retries = 20, delay = 1.second, label = "json patch applied"
      )
      _ <- k8s.delete[Pod](name)
      _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
    yield ()
  }
