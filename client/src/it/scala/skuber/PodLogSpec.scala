package skuber

import java.util.UUID.randomUUID
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import skuber.FutureUtil.FutureOps
import skuber.Pod.LogQueryParams
import skuber.json.format._
import scala.concurrent.duration._

class PodLogSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {
  val podName: String = randomUUID().toString

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  behavior of "PodLog"

  private val idleTimeout = 10.seconds
  override val config = ConfigFactory.parseString(s"skuber.pod-log.idle-timeout=${idleTimeout.toSeconds}s").withFallback(ConfigFactory.load())


  override def afterAll(): Unit = {
    val k8s = k8sInit(config)
    val result = k8s.delete[Pod](podName).withTimeout().recover{ case _ => () }
    result.futureValue
    result.onComplete { _ =>
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }
  }

  it should "get log of a pod" in { k8s =>
    k8s.create(getNginxPod(podName, "1.7.9")).valueT
    Thread.sleep(3000)
    eventually(timeout(30.seconds), interval(3.seconds)) {
      val source = k8s.getPodLogSource(podName, LogQueryParams(follow = Some(false))).futureValue
      val log = source.map(_.utf8String).runReduce(_ + _).futureValue
      assert(log == "foo\n")
    }
  }

  def getNginxContainerArgs(version: String): Container = Container(name = "ubuntu", image = "nginx:" + version,
    command = List("sh"),
    args = List("-c", s"""echo "foo"; trap exit TERM; sleep infinity & wait"""))

  def getNginxPod(name: String, version: String): Pod = {
    val container = getNginxContainerArgs(version)
    val podSpec = Pod.Spec(containers = List((container)))
    Pod.named(name).copy(spec = Some(podSpec))
  }
}
