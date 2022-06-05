package skuber

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{Outcome, fixture}
import scala.concurrent.ExecutionContextExecutor


trait K8SFixture extends fixture.FlatSpec {

  override type FixtureParam = K8SRequestContext

  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

  val config: Config = ConfigFactory.load()

  override def withFixture(test: OneArgTest): Outcome = {
    val k8s = k8sInit(config)
    try {
      test(k8s)
    } finally {
      k8s.close
    }
  }
}
