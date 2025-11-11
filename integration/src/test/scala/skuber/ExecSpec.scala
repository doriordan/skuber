package skuber

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import skuber.api.client.K8SException
import skuber.json.format.podFormat
import skuber.model.{Container, Pod}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future, Promise}

abstract class ExecSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll {
  val nginxPodName: String = java.util.UUID.randomUUID().toString

  behavior of "Exec"

  override def beforeAll(): Unit = {
    super.beforeAll()

    val k8s = createK8sClient(config)
    Await.ready(k8s.create(getNginxPod(nginxPodName)), 3.second)
    // Let the pod run
    Thread.sleep(3000)
    k8s.close()
  }

  override def afterAll(): Unit = {
    val k8s = createK8sClient(config)
    Await.ready(k8s.delete[Pod](nginxPodName), 3.second)
    Thread.sleep(3000)
    k8s.close()

    super.afterAll()
  }

  def closeAfter(duration: Duration): Promise[Unit] = {
    val promise = Promise[Unit]()
    Future {
      Thread.sleep(duration.toMillis)
      promise.success(())
    }
    promise
  }
}
