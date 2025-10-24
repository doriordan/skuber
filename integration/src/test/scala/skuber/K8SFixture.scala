package skuber

import com.typesafe.config.ConfigFactory
import org.scalatest.FutureOutcome
import org.scalatest.flatspec.FixtureAsyncFlatSpec
import skuber.api.client.KubernetesClient

import scala.concurrent.ExecutionContext

/**
 * Shared fixture base class for integration tests that provides access to Kubernetes clients. Concrete fixtures are in the Pekko/Akka modules.
 */

trait K8SFixture[SB, SI, SO] extends FixtureAsyncFlatSpec {

  override type FixtureParam = KubernetesClient[SB, SI, SO]

  implicit def executionContext: ExecutionContext

  val config = ConfigFactory.load()

  /**
    * Create a Kubernetes client. Implementation is provided by a subclass fixture
    * that extends this in (either Akka or Pekko specific).
    */
  def createK8sClient(config: com.typesafe.config.Config): FixtureParam

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val k8s = createK8sClient(config)

    complete {
      withFixture(test.toNoArgAsyncTest(k8s))
    } lastly {
      k8s.close()
    }
  }
}