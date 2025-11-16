package skuber

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.{Assertion, FutureOutcome}
import skuber.akkaclient._
import skuber.akkaclient.impl.AkkaKubernetesClientImpl
import skuber.api.client.KubernetesClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Akka-specific implementation of the shared K8S fixture, creating an Akka Kubernetes client for use by tests
 */
trait AkkaK8SFixture extends K8SFixture {

  implicit def system: ActorSystem = ActorSystem()

  override implicit def executionContext: ExecutionContext =  ExecutionContext.global

  override def createK8sClient(config: com.typesafe.config.Config): AkkaKubernetesClient = {
    k8sInit(config)
  }

  def withAkkaK8sClient(test: AkkaKubernetesClient => Future[Assertion],  timeout: Duration = 30.seconds): Future[Assertion] = {
    val actorSystem=system
    val k8s = k8sInit(config)(actorSystem)
    try {
      val result = Await.result(test(k8s), timeout)
      Future { result }
    } finally {
      k8s.close()
      actorSystem.terminate()
    }
  }
}