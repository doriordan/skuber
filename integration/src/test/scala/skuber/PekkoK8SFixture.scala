package skuber

import org.apache.pekko.actor.ActorSystem
import skuber.pekkoclient._

import scala.concurrent.ExecutionContext

/**
 * Pekko-specific implementation of the shared K8S fixture, for use with tests that require the specific Pekko client
  * type
 */
trait PekkoK8SFixture extends K8SFixture[PekkoSB, PekkoSI, PekkoSO] {

  implicit val system: ActorSystem = ActorSystem()
  override implicit def executionContext: ExecutionContext = system.dispatcher

  override def createK8sClient(config: com.typesafe.config.Config): PekkoKubernetesClient = {
    k8sInit(config)
  }
}