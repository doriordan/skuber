package skuber

import akka.Done
import akka.stream.scaladsl.{Sink, Source}
import skuber.api.client.K8SException

import scala.concurrent.Future
import scala.concurrent.duration._


class AkkaExecSpec extends ExecSpec with AkkaK8SFixture {

  it should "execute a command in the specified container of the running pod" in {
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    withAkkaK8sClient { k8s =>
      k8s.exec(nginxPodName, Seq("whoami"), maybeContainerName = Some("nginx"),
        maybeStdout = Some(stdout), maybeStderr = Some(stderr), maybeClose = Some(closeAfter(1.second))).map { _ =>
        assert(output == "root\n")
        assert(errorOutput == "")
      }
    }
  }

  it should "execute a command in the running pod" in {
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]]  = Sink.foreach(errorOutput += _)
    withAkkaK8sClient { k8s =>
      k8s.exec(
        podName = nginxPodName,
        command = Seq("whoami"),
        maybeContainerName = None,
        maybeStdin = None,
        maybeStdout = Some(stdout),
        maybeStderr = Some(stderr),
        maybeClose = Some(closeAfter(1.second))
      ).map { _ =>
        assert(output == "root\n")
        assert(errorOutput == "")
      }
    }
  }

  it should "execute a command that outputs to stderr in the running pod" in {
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    withAkkaK8sClient { k8s =>
      k8s.exec(nginxPodName, Seq("sh", "-c", "whoami >&2"),
        maybeStdout = Some(stdout), maybeStderr = Some(stderr), maybeClose = Some(closeAfter(1.second))).map { _ =>
        assert(output == "")
        assert(errorOutput == "root\n")
      }
    }
  }

  it should "execute a command in an interactive shell of the running pod" in {
    val stdin = Source.single("whoami\n")
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    withAkkaK8sClient { k8s =>
      k8s.exec(nginxPodName, Seq("sh"), maybeStdin = Some(stdin),
        maybeStdout = Some(stdout), maybeStderr = Some(stderr), tty = true, maybeClose = Some(closeAfter(1.second))).map { _ =>
        output should include("whoami")
        output should include("root")
        assert(errorOutput == "")
      }
    }
  }

  it should "throw an exception without stdin, stdout nor stderr in the running pod" in {
    withAkkaK8sClient { k8s =>
      k8s.exec(podName = nginxPodName, command = Seq("whoami")).failed.map {
        case e: K8SException =>
          assert(e.status.code.contains(400))
      }
    }
  }

  it should "throw an exception against an unexisting pod" in {
    withAkkaK8sClient { k8s =>
      k8s.exec(podName = nginxPodName + "x", command = Seq("whoami")).failed.map {
        case e: K8SException =>
          assert(e.status.code.contains(404))
      }
    }
  }
}
