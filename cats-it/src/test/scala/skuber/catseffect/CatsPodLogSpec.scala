package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import skuber.api.client.{LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.{Container, Pod}
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*

class CatsPodLogSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 5.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  def makeLogPod(name: String): Pod =
    val container = Container(
      name = "nginx", image = "nginx:1.29.2",
      command = List("sh"),
      args = List("-c", """echo "foo"; trap exit TERM; sleep infinity & wait""")
    )
    Pod.named(name).copy(spec = Some(Pod.Spec(containers = List(container))))

  client.test("get pod log") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(makeLogPod(name)).map(_.getOrElse(fail("Create failed")))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.status.exists(_.phase.contains(Pod.Phase.Running)))),
        retries = 30, delay = 3.seconds, label = "pod running"
      )
      log <- k8s.getPodLogStream(name, Pod.LogQueryParams(follow = Some(false)))
        .through(fs2.text.utf8.decode)
        .compile.string
      _ = assertEquals(log, "foo\n")
      _ <- k8s.delete[Pod](name)
      _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
    yield ()
  }

  client.test("tail pod log terminates") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(makeLogPod(name)).map(_.getOrElse(fail("Create failed")))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.status.exists(_.phase.contains(Pod.Phase.Running)))),
        retries = 30, delay = 3.seconds, label = "pod running"
      )
      log <- k8s.getPodLogStream(name, Pod.LogQueryParams(follow = Some(true)))
        .through(fs2.text.utf8.decode)
        .interruptAfter(15.seconds)
        .compile.string
      _ = assert(log.startsWith("foo\n"), s"Expected log starting with 'foo\\n' but got: $log")
      _ <- k8s.delete[Pod](name)
      _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
    yield ()
  }
