package skuber

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import skuber.json.format.podFormat
import skuber.model.apps.v1.Deployment
import skuber.model.{Container, LabelSelector, Pod}

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class WatchSpec extends K8SFixture[_, _, _] with Eventually with Matchers with BeforeAndAfterAll {

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(200, Seconds), interval = Span(5, Seconds))

  val podName: String = java.util.UUID.randomUUID().toString

  behavior of "Watch"

  val idleTimeout = 3.seconds
  override val config = ConfigFactory.parseString(s"skuber.pod-log.idle-timeout=${idleTimeout.toSeconds}s").withFallback(ConfigFactory.load())

  override def beforeAll(): Unit = {
    super.beforeAll()

    val k8s = createK8sClient(config)
    Await.result(k8s.create(getNginxPod(podName)), 60.second)
    // Let the pod running
    Thread.sleep(3000)
    k8s.close()
  }

  override def afterAll(): Unit = {
    val k8s = createK8sClient(config)
    Await.result(k8s.delete[Pod](podName), 3.second)
    Thread.sleep(3000)
    k8s.close()

    super.afterAll()
  }

  def getNginxContainerForWatch(version: String = defaultNginxVersion): Container = Container(
    name = "nginx", image = "nginx:" + version,
    command = List("sh"),
    args = List("-c", s"""echo "foo"; trap exit TERM; sleep infinity & wait""")
  )

  def getNginxPodForWatch(name: String): Pod = {
    val container = getNginxContainerForWatch()
    val podSpec = Pod.Spec(containers = List((container)))
    Pod.named(podName).copy(spec = Some(podSpec))
  }

  def getNginxDeploymentForWatch(name: String): Deployment = {
    import LabelSelector.dsl._

    import scala.language.reflectiveCalls
    val nginxContainer = getNginxContainerForWatch()
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(nginxTemplate).withLabelSelector("app" is "nginx")
  }
}
