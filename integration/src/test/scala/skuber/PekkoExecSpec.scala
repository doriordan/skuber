package skuber

import org.apache.pekko
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import skuber.api.client.K8SException

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Pod exec tests that require the Pekko concrete client as fixture
  */
class PekkoExecSpec extends ExecSpec with PekkoK8SFixture {

  it should "execute a command in the specified container of the running pod" in {
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    withPekkoK8sClient { k8s =>
      k8s.exec(nginxPodName, Seq("whoami"),
        maybeContainerName = Some("nginx"),
        maybeStdout = Some(stdout),
        maybeStderr = Some(stderr),
        maybeClose = Some(closeAfter(5.second))
      ).map { _ =>
        Thread.sleep(1000)
        assert(output == "root\n")
        assert(errorOutput == "")
      }
    }
  }

  it should "execute a command in the running pod" in {
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    withPekkoK8sClient { k8s =>
      k8s.exec(nginxPodName, Seq("whoami"), maybeStdout = Some(stdout), maybeStderr = Some(stderr), maybeClose = Some(closeAfter(5.second))).map { _ =>
        Thread.sleep(10000)
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
    withPekkoK8sClient { k8s =>
      k8s.exec(nginxPodName, Seq("sh", "-c", "whoami >&2"),
        maybeStdout = Some(stdout), maybeStderr = Some(stderr), maybeClose = Some(closeAfter(5.second))).map { _ =>
        Thread.sleep(1000)
        assert(output == "")
        assert(errorOutput == "root\n")
      }
    }
  }

  it should "execute a command in an interactive shell of the running pod" in {
    Thread.sleep(10000)
    val stdin = Source.single("whoami\n")
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    withPekkoK8sClient { k8s =>
      k8s.exec(nginxPodName, Seq("sh"), maybeStdin = Some(stdin),
        maybeStdout = Some(stdout), maybeStderr = Some(stderr), tty = true, maybeClose = Some(closeAfter(5.second))).map { _ =>
        Thread.sleep(1000)
        output should include("whoami")
        output should include("root")
        assert(errorOutput == "")
      }
    }
  }

  it should "throw an exception without stdin, stdout nor stderr in the running pod" in {
    withPekkoK8sClient { k8s =>
      k8s.exec(podName = nginxPodName, command = Seq("whoami")).failed.map {
        case e: K8SException =>
          assert(e.status.code.contains(400))
      }
    }
  }

  it should "throw an exception against an unexisting pod" in {
    withPekkoK8sClient { k8s =>
      k8s.exec(podName = nginxPodName + "x", command = Seq("whoami")).failed.map {
        case e: K8SException =>
          assert(e.status.code.contains(404))
      }
    }
  }
}
