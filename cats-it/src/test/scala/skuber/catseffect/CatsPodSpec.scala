package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import skuber.api.client.{LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.{LabelSelector, Pod, PodList}
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*

class CatsPodSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 5.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  client.test("pod CRUD lifecycle") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      created <- k8s.create(getNginxPod(name))
        .map(_.getOrElse(fail("Create failed")))
      _ = assertEquals(created.name, name)
      got <- k8s.get[Pod](name)
        .map(_.getOrElse(fail("Get failed")))
      _ = assertEquals(got.name, name)
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists { p =>
          p.status.exists { status =>
            status.phase.contains(Pod.Phase.Running) &&
            status.conditions.exists(c => c._type == "Ready" && c.status == "True")
          }
        }),
        retries = 40, delay = 3.seconds, label = "pod ready"
      )
      _ <- k8s.delete[Pod](name)
      _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
    yield ()
  }

  client.test("delete selected pods") { k8s =>
    val uuid = java.util.UUID.randomUUID().toString
    val fooName = s"foo-$uuid"
    val barName = s"bar-$uuid"
    val commonLabel = "test-group" -> uuid
    for
      _ <- k8s.create(getNginxPodWithLabels(fooName, Map(commonLabel, "foo" -> "1")))
        .map(_.getOrElse(fail("Create foo pod failed")))
      _ <- k8s.create(getNginxPodWithLabels(barName, Map(commonLabel, "bar" -> "2")))
        .map(_.getOrElse(fail("Create bar pod failed")))
      fooList <- k8s.listSelected[PodList](LabelSelector(LabelSelector.ExistsRequirement("foo")))
        .map(_.getOrElse(fail("listSelected failed")))
      _ = assert(fooList.items.exists(_.name == fooName), s"Expected $fooName in foo list")
      _ <- fooList.items.foldLeft(IO.unit) { (acc, pod) =>
        acc >> k8s.delete[Pod](pod.name).map(_ => ())
      }
      _ <- retryUntilGone(k8s.get[Pod](fooName), retries = 40, delay = 3.seconds)
      barStillExists <- k8s.get[Pod](barName).map(_.isRight)
      _ = assert(barStillExists, s"Expected $barName to still exist")
      _ <- k8s.delete[Pod](barName)
      _ <- retryUntilGone(k8s.get[Pod](barName), retries = 40, delay = 3.seconds)
    yield ()
  }
