package skuber

import java.time.ZonedDateTime
import akka.stream.scaladsl.TcpIdleTimeoutException
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import skuber.akkaclient.k8sInit
import skuber.model.Pod.LogQueryParams
import skuber.json.format._
import skuber.model.{Container, Pod}

import scala.concurrent.Await
import scala.concurrent.duration._

class PodLogSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll {
  val podName: String = java.util.UUID.randomUUID().toString

  behavior of "PodLog"

  val idleTimeout = 3.seconds
  override val config = ConfigFactory.parseString(s"skuber.pod-log.idle-timeout=${idleTimeout.toSeconds}s").withFallback(ConfigFactory.load())

  override def beforeAll(): Unit = {
    super.beforeAll()

    val k8s = k8sInit(config)
    Await.result(k8s.create(getNginxPod(podName, "1.27.2")), 3.second)
    // Let the pod running
    Thread.sleep(3000)
    k8s.close()
  }

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)
    Await.result(k8s.delete[Pod](podName), 3.second)
    Thread.sleep(3000)
    k8s.close()

    super.afterAll()
  }

  it should "get log of a pod" in { k8s =>
    k8s.getPodLogSource(podName, LogQueryParams(follow = Some(false))).flatMap { source =>
      source.map(_.utf8String).runReduce(_ + _).map { s =>
        assert(s == "foo\n")
      }
    }
  }

  it should "tail log of a pod and timeout after a while" in { k8s =>
    var log = ""
    var start = ZonedDateTime.now()
    k8s.getPodLogSource(podName, LogQueryParams(follow = Some(true))).flatMap { source =>
      source.map(_.utf8String).runForeach(log += _)
    }.failed.map { case e: TcpIdleTimeoutException =>
      val msgPattern = s"TCP idle-timeout encountered on connection to [^,]+, no bytes passed in the last ${idleTimeout}"
      assert(e.getMessage.matches(msgPattern), s"""["${e.getMessage}"] does not match ["${msgPattern}"]""")
      assert(log == "foo\n")
      assert(ZonedDateTime.now().isAfter(start.withSecond(idleTimeout.toSeconds.toInt)))
    }
  }

  def getNginxContainer(version: String): Container = Container(
    name = "ubuntu", image = "nginx:" + version,
    command = List("sh"),
    args = List("-c", s"""echo "foo"; trap exit TERM; sleep infinity & wait""")
  )

  def getNginxPod(name: String, version: String): Pod = {
    val container = getNginxContainer(version)
    val podSpec = Pod.Spec(containers = List((container)))
    Pod.named(podName).copy(spec = Some(podSpec))
  }
}
