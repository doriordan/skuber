package skuber

import akka.actor.ActorSystem
import skuber.akkaclient._

import scala.concurrent.ExecutionContext

/**
 * Akka-specific implementation of the shared K8S fixture, creating an Akka Kubernetes client for use by tests
 */
trait AkkaK8SFixture extends K8SFixture[AkkaSB, AkkaSI, AkkaSO] {

  implicit val system: ActorSystem = ActorSystem()
  override implicit def executionContext: ExecutionContext = system.dispatcher

  override def createK8sClient(config: com.typesafe.config.Config): AkkaKubernetesClient = {
    k8sInit(config)
  }
}