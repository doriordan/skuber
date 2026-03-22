package skuber.zioit

import zio.*
import zio.stream.*
import zio.test.*
import zio.test.test
import skuber.json.format.*
import skuber.model.Pod
import skuber.zio.{ExecOutput, ZKubernetesClient}
import skuber.zioit.TestHelpers.*

object ZioExecSpec:

  def spec = suite("ZIO Exec")(

    test("exec whoami in specified container") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(getNginxPod(name))
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.status.exists(_.phase.contains(Pod.Phase.Running))),
            retries = 30, delay = 3.seconds, label = "pod running"
          )
          output <- k8s.exec(name, Seq("whoami"), containerName = Some("nginx"))
            .interruptAfter(10.seconds)
            .collect { case ExecOutput.Stdout(d) => d }
            .runFold("")(_ + _)
          _ = assert(output.trim == "root", s"Expected 'root' but got: $output")
          _ <- k8s.delete[Pod](name)
          _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    },

    test("exec whoami in default container") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(getNginxPod(name))
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.status.exists(_.phase.contains(Pod.Phase.Running))),
            retries = 30, delay = 3.seconds, label = "pod running"
          )
          output <- k8s.exec(name, Seq("whoami"))
            .interruptAfter(10.seconds)
            .collect { case ExecOutput.Stdout(d) => d }
            .runFold("")(_ + _)
          _ = assert(output.trim == "root", s"Expected 'root' but got: $output")
          _ <- k8s.delete[Pod](name)
          _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    },

    test("exec command that outputs to stderr") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(getNginxPod(name))
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.status.exists(_.phase.contains(Pod.Phase.Running))),
            retries = 30, delay = 3.seconds, label = "pod running"
          )
          outputs <- k8s.exec(name, Seq("sh", "-c", "whoami >&2"))
            .interruptAfter(10.seconds)
            .runCollect
          stdout = outputs.collect { case ExecOutput.Stdout(d) => d }.mkString
          stderr = outputs.collect { case ExecOutput.Stderr(d) => d }.mkString
          _ = assert(stdout == "", s"Expected empty stdout but got: $stdout")
          _ = assert(stderr.trim == "root", s"Expected 'root' in stderr but got: $stderr")
          _ <- k8s.delete[Pod](name)
          _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    },

    test("exec interactive shell with stdin") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(getNginxPod(name))
          _ <- retryUntil(
            k8s.get[Pod](name).map(_.status.exists(_.phase.contains(Pod.Phase.Running))),
            retries = 30, delay = 3.seconds, label = "pod running"
          )
          stdin = ZStream.succeed("whoami\n")
          output <- k8s.exec(name, Seq("sh"), stdin = Some(stdin), tty = true)
            .interruptAfter(10.seconds)
            .collect { case ExecOutput.Stdout(d) => d }
            .runFold("")(_ + _)
          _ = assert(output.contains("root"), s"Expected 'root' in output but got: $output")
          _ <- k8s.delete[Pod](name)
          _ <- retryUntilGone(k8s.get[Pod](name), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    },

    test("exec against non-existent pod fails or returns error") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString + "x"
        k8s.exec(name, Seq("whoami"))
          .runCollect
          .fold(
            _ => assertTrue(true),
            outputs => assertTrue(outputs.exists { case ExecOutput.Stderr(_) => true; case _ => false })
          )
      }
    }

  )
