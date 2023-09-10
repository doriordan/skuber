package skuber

import akka.actor.ActorSystem
import org.scalatest.FutureOutcome
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.FixtureAsyncFlatSpec

import skuber.akkaclient._

trait K8SFixture extends FixtureAsyncFlatSpec {

  override type FixtureParam =  AkkaKubernetesClient

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val config = ConfigFactory.load()

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val k8s = k8sInit(config)

    complete {
      withFixture(test.toNoArgAsyncTest(k8s))
    } lastly {
      k8s.close
    }
  }
}
