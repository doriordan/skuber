package skuber

import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import skuber.json.format._

import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}


class ExecSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll {
  val nginxPodName: String = java.util.UUID.randomUUID().toString

  behavior of "Exec"

  override def beforeAll(): Unit = {
    super.beforeAll()

    val k8s = k8sInit
    Await.result(k8s.create(getNginxPod(nginxPodName, "1.7.9")), 3.second)
    // Let the pod running
    Thread.sleep(3000)
    k8s.close
  }

  override def afterAll(): Unit = {
    val k8s = k8sInit
    Await.result(k8s.delete[Pod](nginxPodName), 3.second)
    Thread.sleep(3000)
    k8s.close

    super.afterAll()
  }

  it should "execute a command in the running pod" in { k8s =>
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    k8s.exec(nginxPodName, Seq("whoami"), maybeStdout = Some(stdout), maybeStderr = Some(stderr), maybeClose = Some(closeAfter(1.second))).map { _ =>
      assert(output == "root\n")
      assert(errorOutput == "")
    }
  }

  it should "execute a command in the specified container of the running pod" in { k8s =>
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    k8s.exec(nginxPodName, Seq("whoami"), maybeContainerName = Some("nginx"),
      maybeStdout = Some(stdout), maybeStderr = Some(stderr), maybeClose = Some(closeAfter(1.second))).map { _ =>
      assert(output == "root\n")
      assert(errorOutput == "")
    }
  }

  it should "execute a command that outputs to stderr in the running pod" in { k8s =>
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    k8s.exec(nginxPodName, Seq("sh", "-c", "whoami >&2"),
      maybeStdout = Some(stdout), maybeStderr = Some(stderr), maybeClose = Some(closeAfter(1.second))).map { _ =>
      assert(output == "")
      assert(errorOutput == "root\n")
    }
  }

  it should "execute a command in an interactive shell of the running pod" in { k8s =>
    val stdin = Source.single("whoami\n")
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    k8s.exec(nginxPodName, Seq("sh"), maybeStdin = Some(stdin),
      maybeStdout = Some(stdout), maybeStderr = Some(stderr), tty = true, maybeClose = Some(closeAfter(1.second))).map { _ =>
      output should include("whoami")
      output should include("root")
      assert(errorOutput == "")
    }
  }

  it should "throw an exception without stdin, stdout nor stderr in the running pod" in { k8s =>
    k8s.exec(nginxPodName, Seq("whoami")).failed.map {
      case e: K8SException =>
        assert(e.status.code == Some(400))
    }
  }

  it should "throw an exception against an unexisting pod" in { k8s =>
    k8s.exec(nginxPodName + "x", Seq("whoami")).failed.map {
      case e: K8SException =>
        assert(e.status.code == Some(404))
    }
  }

  def closeAfter(duration: Duration) = {
    val promise = Promise[Unit]()
    Future {
      Thread.sleep(duration.toMillis)
      promise.success(())
    }
    promise
  }

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)

  def getNginxPod(name: String, version: String): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec = Pod.Spec(containers = List((nginxContainer)))
    Pod.named(nginxPodName).copy(spec = Some(nginxPodSpec))
  }
}
