package skuber.zioit

import zio.*
import zio.stream.*
import zio.test.*
import zio.test.test
import skuber.json.format.*
import skuber.model.{Container, Pod}
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

object ZioPodLogSpec:

  def makeLogPod(name: String): Pod =
    val container = Container(
      name = "nginx", image = "nginx:1.29.2",
      command = List("sh"),
      args = List("-c", """echo "foo"; trap exit TERM; sleep infinity & wait""")
    )
    Pod.named(name).copy(spec = Some(Pod.Spec(containers = List(container))))

  def spec = suite("ZIO Pod Logs")(

    test("get pod log") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(makeLogPod(name))
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.status.exists(_.phase.contains(Pod.Phase.Running))),
            retries = 30, delay = 3.seconds, label = "pod running"
          )
          log <- k8s.getPodLogStream(name, Pod.LogQueryParams(follow = Some(false)))
            .via(ZPipeline.utf8Decode)
            .runFold("")(_ + _)
          _ = assert(log == "foo\n", s"Expected 'foo\\n' but got: $log")
          _ <- k8s.delete[Pod](name)
          _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    },

    test("tail pod log terminates") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(makeLogPod(name))
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.status.exists(_.phase.contains(Pod.Phase.Running))),
            retries = 30, delay = 3.seconds, label = "pod running"
          )
          log <- k8s.getPodLogStream(name, Pod.LogQueryParams(follow = Some(true)))
            .via(ZPipeline.utf8Decode)
            .interruptAfter(15.seconds)
            .runFold("")(_ + _)
          _ = assert(log.startsWith("foo\n"), s"Expected log starting with 'foo\\n' but got: $log")
          _ <- k8s.delete[Pod](name)
          _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    }

  )
