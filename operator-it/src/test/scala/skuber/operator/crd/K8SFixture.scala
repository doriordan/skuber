package skuber.operator.crd

import com.typesafe.config.ConfigFactory
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import skuber.api.client.KubernetesClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Shared base class for operator integration tests that provides access to Kubernetes clients.
 */
trait K8SFixture extends AsyncFlatSpec {

  implicit def executionContext: ExecutionContext

  val config = ConfigFactory.load()

  /**
   * Create a Kubernetes client. Implementation is provided by a subclass fixture
   * (either Akka or Pekko specific).
   */
  def createK8sClient(config: com.typesafe.config.Config): KubernetesClient[?, ?, ?]

  def withK8sClient(test: KubernetesClient[?, ?, ?] => Future[Assertion], timeout: Duration = 30.seconds): Future[Assertion] = {
    val k8s = createK8sClient(config)

    try {
      val result = Await.result(test(k8s), timeout)
      Future { result }
    } finally {
      k8s.close()
    }
  }
}
