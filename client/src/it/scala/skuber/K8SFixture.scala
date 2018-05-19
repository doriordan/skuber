package skuber

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.{FutureOutcome, fixture}
import skuber.api.client._
import com.typesafe.config.ConfigFactory

trait K8SFixture extends fixture.AsyncFlatSpec {

  override type FixtureParam =  RequestContext

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val k8s = init(defaultK8sConfig.currentContext, LoggingConfig(logResponseFullListResource = true), None, ConfigFactory.load())
    complete {
      withFixture(test.toNoArgAsyncTest(k8s))
    } lastly {
      k8s.close
    }
  }
}
