package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import skuber.api.client.{LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.Pod
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*

class CatsExecSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 5.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  client.test("exec whoami in specified container") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(getNginxPod(name)).map(_.getOrElse(fail("Create failed")))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.status.exists(_.phase.contains(Pod.Phase.Running)))),
        retries = 30, delay = 3.seconds, label = "pod running"
      )
      output <- k8s.exec(name, Seq("whoami"), containerName = Some("nginx"))
        .interruptAfter(10.seconds)
        .collect { case ExecOutput.Stdout(d) => d }
        .compile.string
      _ = assertEquals(output.trim, "root")
      _ <- k8s.delete[Pod](name)
      _ <- retryUntilGone(k8s.get[Pod](name), delay = 3.seconds)
    yield ()
  }

  client.test("exec whoami in default container") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(getNginxPod(name)).map(_.getOrElse(fail("Create failed")))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.status.exists(_.phase.contains(Pod.Phase.Running)))),
        retries = 30, delay = 3.seconds, label = "pod running"
      )
      output <- k8s.exec(name, Seq("whoami"))
        .interruptAfter(10.seconds)
        .collect { case ExecOutput.Stdout(d) => d }
        .compile.string
      _ = assertEquals(output.trim, "root")
      _ <- k8s.delete[Pod](name)
      _ <- retryUntilGone(k8s.get[Pod](name), delay = 3.seconds)
    yield ()
  }

  client.test("exec command that outputs to stderr") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(getNginxPod(name)).map(_.getOrElse(fail("Create failed")))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.status.exists(_.phase.contains(Pod.Phase.Running)))),
        retries = 30, delay = 3.seconds, label = "pod running"
      )
      outputs <- k8s.exec(name, Seq("sh", "-c", "whoami >&2"))
        .interruptAfter(10.seconds)
        .compile.toList
      _ = assertEquals(outputs.collect { case ExecOutput.Stdout(d) => d }.mkString, "")
      _ = assertEquals(outputs.collect { case ExecOutput.Stderr(d) => d }.mkString.trim, "root")
      _ <- k8s.delete[Pod](name)
      _ <- retryUntilGone(k8s.get[Pod](name), delay = 3.seconds)
    yield ()
  }

  client.test("exec interactive shell with stdin") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(getNginxPod(name)).map(_.getOrElse(fail("Create failed")))
      _ <- retryUntil(
        k8s.get[Pod](name).map(_.exists(_.status.exists(_.phase.contains(Pod.Phase.Running)))),
        retries = 30, delay = 3.seconds, label = "pod running"
      )
      stdin = fs2.Stream.emit("whoami\n")
      output <- k8s.exec(name, Seq("sh"), stdin = Some(stdin), tty = true)
        .interruptAfter(10.seconds)
        .collect { case ExecOutput.Stdout(d) => d }
        .compile.string
      _ = assert(output.contains("root"), s"Expected 'root' in output but got: $output")
      _ <- k8s.delete[Pod](name)
      _ <- retryUntilGone(k8s.get[Pod](name), delay = 3.seconds)
    yield ()
  }

  client.test("exec against non-existent pod fails or returns error") { k8s =>
    val name = java.util.UUID.randomUUID().toString + "x"
    k8s.exec(name, Seq("whoami"))
      .compile.toList
      .attempt
      .map { result =>
        // Stream should either fail with an exception or return error output — not silently succeed
        assert(
          result.isLeft || result.toOption.get.exists {
            case ExecOutput.Stderr(_) => true
            case _ => false
          },
          s"Expected exec against non-existent pod to fail, got: $result"
        )
      }
  }
