package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import skuber.json.format.*
import skuber.model.{LabelSelector, Pod, PodList}
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

object ZioPodSpec:

  def spec = suite("ZIO Pod")(

    test("pod CRUD lifecycle") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          created <- k8s.create(getNginxPod(name))
          _ = assert(created.name == name)
          got <- k8s.get[Pod](name)
          _ = assert(got.name == name)
          _ <- retryUntil(
            k8s.get[Pod](name).map { p =>
              p.status.exists { s =>
                s.phase.contains(Pod.Phase.Running) &&
                s.conditions.exists(c => c._type == "Ready" && c.status == "True")
              }
            },
            retries = 40, delay = 3.seconds, label = "pod ready"
          )
          _ <- k8s.delete[Pod](name)
          _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    },

    test("delete selected pods") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val uuid = java.util.UUID.randomUUID().toString
        val fooName = s"foo-$uuid"
        val barName = s"bar-$uuid"
        val commonLabel = "test-group" -> uuid
        for
          _ <- k8s.create(getNginxPodWithLabels(fooName, Map(commonLabel, "foo" -> "1")))
          _ <- k8s.create(getNginxPodWithLabels(barName, Map(commonLabel, "bar" -> "2")))
          fooList <- k8s.listSelected[PodList](LabelSelector(LabelSelector.ExistsRequirement("foo")))
          _ = assert(fooList.items.exists(_.name == fooName), s"Expected $fooName in foo list")
          _ <- ZIO.foreachDiscard(fooList.items)(pod => k8s.delete[Pod](pod.name))
          _ <- retryUntilGone(k8s.get[Pod](fooName), retries = 40, delay = 3.seconds)
          barStillExists <- k8s.getOption[Pod](barName).map(_.isDefined)
          _ = assert(barStillExists, s"Expected $barName to still exist")
          _ <- k8s.delete[Pod](barName)
          _ <- retryUntilGone(k8s.get[Pod](barName), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    }

  )
