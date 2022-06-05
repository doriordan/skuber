package skuber

import akka.actor.ActorSystem
import org.scalatest.{FutureOutcome, fixture}
import skuber.api.client._
import com.typesafe.config.ConfigFactory
import skuber.api.client.impl.KubernetesClientImpl

trait K8SFixture extends fixture.AsyncFlatSpec {

  override type FixtureParam =  K8SRequestContext

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val config = ConfigFactory.load()

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val k8s = k8sInit(config)
    complete {
      withFixture(test.toNoArgAsyncTest(k8s))
    } lastly {
      k8s.close
      system.terminate()
    }
  }
}
