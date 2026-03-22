package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import skuber.api.patch.*
import skuber.json.format.*
import skuber.model.{ObjectMeta, Pod}
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

object ZioPatchSpec:

  def spec = suite("ZIO Patch")(

    test("patch pod with multiple strategies") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        val pod = Pod(
          metadata = ObjectMeta(name = name, labels = Map("label" -> "1"), annotations = Map("annotation" -> "1")),
          spec = Some(Pod.Spec(containers = List(getNginxContainer())))
        )
        for
          _ <- k8s.create(pod)
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.status.exists(_.phase.contains(Pod.Phase.Running))),
            retries = 30, delay = 3.seconds, label = "pod running"
          )
          r1 = java.util.UUID.randomUUID().toString
          _ <- k8s.patch[MetadataPatch, Pod](name, MetadataPatch(labels = Some(Map("foo" -> r1)), annotations = None))
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.metadata.labels.get("foo").contains(r1)),
            retries = 20, delay = 1.second, label = "strategic merge patch applied"
          )
          r2 = java.util.UUID.randomUUID().toString
          _ <- k8s.patch[MetadataPatch, Pod](name, MetadataPatch(labels = Some(Map("foo" -> r2)), annotations = None, strategy = StrategicMergePatchStrategy))
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.metadata.labels.get("foo").contains(r2)),
            retries = 20, delay = 1.second, label = "explicit strategic merge applied"
          )
          r3 = java.util.UUID.randomUUID().toString
          _ <- k8s.patch[MetadataPatch, Pod](name, MetadataPatch(labels = Some(Map("foo" -> r3)), annotations = None, strategy = JsonMergePatchStrategy))
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.metadata.labels.get("foo").contains(r3)),
            retries = 20, delay = 1.second, label = "json merge patch applied"
          )
          r4 = java.util.UUID.randomUUID().toString
          _ <- k8s.patch[JsonPatch, Pod](name, JsonPatch(List(
            JsonPatchOperation.Add("/metadata/labels/foo", r4)
          )))
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.metadata.labels.get("foo").contains(r4)),
            retries = 20, delay = 1.second, label = "json patch applied"
          )
          _ <- k8s.delete[Pod](name)
          _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    }

  )
