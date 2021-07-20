package skuber

import akka.stream.scaladsl.{Keep, Sink}
import org.scalatest.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.{Await, Future}
import skuber.json.format._

/**
  * @author David O'Riordan
  */
class StreamingCollectionSpec extends K8SFixture with Eventually with Matchers with ScalaFutures {

  behavior of "ResourceCollection"

  it should "stream all items in a collection" in { k8s =>
    val services = for {
      i <- 1 to 10
    } yield getService(s"service$i")
    val created = Future.sequence(services.map { service =>
      k8s.create(service)
    })
    val results = created.flatMap { services =>
      val serviceSource=k8s.stream[Service]
          .chunked(5)
          .source
      serviceSource.toMat(Sink.collection)(Keep.right).run()
    }
    results.futureValue.length should be(10)
  }

  def getService(name: String): Service = {
    val spec: Service.Spec = Service.Spec(ports = List(Service.Port(port = 80)), selector = Map("app" -> "nginx"))
    Service(name, spec)
  }
}
