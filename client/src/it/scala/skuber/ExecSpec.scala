package skuber

import java.util.UUID.randomUUID
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import skuber.FutureUtil.FutureOps
import skuber.json.format._
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Future, Promise}


class ExecSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {
  def getPodName: String = randomUUID().toString
  val namespace1: String = randomUUID().toString
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  private val podName1 = getPodName
  private val podName2 = getPodName
  private val podName3 = getPodName
  private val podName4 = getPodName
  private val podName5 = getPodName
  private val podName6 = getPodName

  behavior of "Exec"

  override def afterAll(): Unit = {
    val k8s = k8sInit
    val results = Future.sequence(List(podName1, podName2, podName3, podName4, podName5, podName6).map { name =>
        k8s.delete[Pod](name).withTimeout().recover { case _ => () }
      }).withTimeout()
    results.futureValue

    results.onComplete { _ =>
      deleteNamespace(namespace1, k8s)
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }
  }

  it should "execute a command in the running pod" in { k8s =>
    println("START: execute a command in the running pod")
    k8s.create(getNginxPod(podName1, "1.7.9")).valueT
    Thread.sleep(5000)

    eventually(timeout(30.seconds), interval(3.seconds)) {
      var output = ""
      var errorOutput = ""
      val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
      val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
      k8s.exec(podName1, Seq("whoami"), maybeStdout = Some(stdout), maybeStderr = Some(stderr), maybeClose = Some(closeAfter(1.second))).valueT

      println("FINISH: execute a command in the running pod")
      assert(output == "root\n")
      assert(errorOutput == "")
    }
  }

  it should "execute a command in the specified container of the running pod" in { k8s =>
    println("START: execute a command in the specified container of the running pod")
    k8s.create(getNginxPod(podName2, "1.7.9")).valueT
    Thread.sleep(5000)

    eventually(timeout(30.seconds), interval(3.seconds)) {
      var output = ""
      val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
      var errorOutput = ""
      val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
      k8s.exec(podName2, Seq("whoami"), maybeContainerName = Some("nginx"), maybeStdout = Some(stdout), maybeStderr = Some(stderr), maybeClose = Some(closeAfter(1.second))).valueT

      println("FINISH: execute a command in the specified container of the running pod")

      assert(output == "root\n")
      assert(errorOutput == "")
    }
  }

  it should "execute a command that outputs to stderr in the running pod" in { k8s =>
    println("START: execute a command that outputs to stderr in the running pod")
    k8s.create(getNginxPod(podName3, "1.7.9")).valueT
    Thread.sleep(5000)
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    k8s.exec(podName3, Seq("sh", "-c", "whoami >&2"), maybeStdout = Some(stdout), maybeStderr = Some(stderr), maybeClose = Some(closeAfter(1.second))).valueT

    println("FINISH: execute a command that outputs to stderr in the running pod")
    assert(output == "")
    assert(errorOutput == "root\n")

  }

  it should "execute a command in an interactive shell of the running pod - specific namespace" in { k8s =>
    println("START: execute a command in an interactive shell of the running pod")
    createNamespace(namespace1, k8s)
    k8s.create(getNginxPod(podName4, "1.7.9"), namespace = Some(namespace1)).valueT
    Thread.sleep(5000)
    val stdin = Source.single("whoami\n")
    var output = ""
    val stdout: Sink[String, Future[Done]] = Sink.foreach(output += _)
    var errorOutput = ""
    val stderr: Sink[String, Future[Done]] = Sink.foreach(errorOutput += _)
    k8s.exec(podName4, Seq("sh"), maybeStdin = Some(stdin), maybeStdout = Some(stdout), maybeStderr = Some(stderr), tty = true, maybeClose = Some(closeAfter(1.second)), namespace = Some(namespace1)).valueT

    println("FINISH: execute a command in an interactive shell of the running pod")

    output should include("whoami")
    output should include("root")
    assert(errorOutput == "")

  }

  it should "throw an exception without stdin, stdout nor stderr in the running pod" in { k8s =>
    println("START: throw an exception without stdin, stdout nor stderr in the running pod")
    k8s.create(getNginxPod(podName5, "1.7.9")).valueT
    Thread.sleep(5000)
    whenReady {
      val res = k8s.exec(podName5, Seq("whoami")).withTimeout().failed
      res
    } { result =>
      println("FINISH: throw an exception without stdin, stdout nor stderr in the running pod")
      result shouldBe a[K8SException]
      result match {
        case ex: K8SException => ex.status.code shouldBe Some(400)
        case _ => assert(false)
      }
    }
  }

  it should "throw an exception against an unexisting pod" in { k8s =>
    println("START: throw an exception against an unexisting pod")
    k8s.create(getNginxPod(podName6, "1.7.9")).valueT
    Thread.sleep(5000)
    whenReady(k8s.exec(podName6 + "x", Seq("whoami")).withTimeout().failed) { result =>
      println("FINISH: throw an exception against an unexisting pod")
      result shouldBe a[K8SException]
      result match {
        case ex: K8SException => ex.status.code shouldBe Some(404)
        case _ => assert(false)
      }
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


  def getNginxPod(name: String, version: String): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec = Pod.Spec(containers = List(nginxContainer))
    Pod.named(name).copy(spec = Some(nginxPodSpec))
  }
}
