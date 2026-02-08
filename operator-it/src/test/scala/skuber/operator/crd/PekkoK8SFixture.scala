package skuber.operator.crd

import org.apache.pekko.actor.ActorSystem
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import skuber.pekkoclient._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Pekko-specific implementation of the shared K8S fixture, creating a Pekko Kubernetes client for use by tests.
 */
trait PekkoK8SFixture extends K8SFixture with BeforeAndAfterAll {

  implicit def system: ActorSystem = ActorSystem()
  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  override def createK8sClient(config: com.typesafe.config.Config): PekkoKubernetesClient = {
    k8sInit(config)
  }

  def withPekkoK8sClient(test: PekkoKubernetesClient => Future[Assertion], timeout: Duration = 30.seconds): Future[Assertion] = {
    val actorSystem = system
    val k8s = k8sInit(config)(using actorSystem)
    try {
      val result = Await.result(test(k8s), timeout)
      Future { result }
    } finally {
      k8s.close()
      actorSystem.terminate()
    }
  }
}
