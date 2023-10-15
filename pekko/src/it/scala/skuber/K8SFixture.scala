package skuber

import org.apache.pekko.actor.ActorSystem
import org.scalatest.FutureOutcome
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.FixtureAsyncFlatSpec
import skuber.pekkoclient._

import scala.concurrent.ExecutionContext

trait K8SFixture extends FixtureAsyncFlatSpec {

  override type FixtureParam =  PekkoKubernetesClient

  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContext  = system.dispatcher

  val config = ConfigFactory.load()

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val k8s = k8sInit(config)

    complete {
      withFixture(test.toNoArgAsyncTest(k8s))
    } lastly {
      k8s.close()
    }
  }
}
