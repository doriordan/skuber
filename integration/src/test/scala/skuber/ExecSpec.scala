package skuber

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import skuber.api.client.K8SException
import skuber.json.format.podFormat
import skuber.model.{Container, Pod}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future, Promise}

abstract class ExecSpec extends K8SFixture[_, _, _] with Eventually with Matchers with BeforeAndAfterAll {
  val nginxPodName: String = java.util.UUID.randomUUID().toString

  behavior of "Exec"

  override def beforeAll(): Unit = {
    super.beforeAll()

    val k8s = createK8sClient(config)
    Await.result(k8s.create(getNginxPod(nginxPodName, "1.7.9")), 3.second)
    // Let the pod run
    Thread.sleep(3000)
    k8s.close()
  }

  override def afterAll(): Unit = {
    val k8s = createK8sClient(config)
    Await.result(k8s.delete[Pod](nginxPodName), 3.second)
    Thread.sleep(3000)
    k8s.close()

    super.afterAll()
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
