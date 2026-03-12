package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import skuber.api.client.{LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.Service

import scala.concurrent.duration.*
import scala.util.Random

class CatsServiceSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 3.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  def makeService(name: String): Service =
    val spec = Service.Spec(ports = List(Service.Port(port = 80)), selector = Map("app" -> "nginx"))
    Service(name, spec)

  client.test("service CRUD lifecycle") { k8s =>
    val name = Random.alphanumeric.filter(_.isLetter).take(20).mkString.toLowerCase
    for
      created <- k8s.create(makeService(name))
        .map(_.getOrElse(fail("Create failed")))
      _ = assertEquals(created.name, name)
      got <- k8s.get[Service](name)
        .map(_.getOrElse(fail("Get failed")))
      _ = assertEquals(got.name, name)
      _ = assertEquals(got.spec.map(_._type), Some(Service.Type.ClusterIP))
      _ <- k8s.delete[Service](name)
      _ <- TestHelpers.retryUntilGone(k8s.get[Service](name), retries = 40, delay = 3.seconds)
    yield ()
  }
