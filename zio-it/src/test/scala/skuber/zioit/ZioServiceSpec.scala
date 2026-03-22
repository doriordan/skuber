package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import skuber.json.format.*
import skuber.model.Service
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

import scala.util.Random

object ZioServiceSpec:

  def makeService(name: String): Service =
    val spec = Service.Spec(ports = List(Service.Port(port = 80)), selector = Map("app" -> "nginx"))
    Service(name, spec)

  def spec = suite("ZIO Service")(

    test("service CRUD lifecycle") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = Random.alphanumeric.filter(_.isLetter).take(20).mkString.toLowerCase
        for
          created <- k8s.create(makeService(name))
          _ = assert(created.name == name)
          got <- k8s.get[Service](name)
          _ = assert(got.name == name)
          _ = assert(got.spec.map(_._type).contains(Service.Type.ClusterIP))
          _ <- k8s.delete[Service](name)
          _ <- retryUntilGone(k8s.get[Service](name), retries = 40, delay = 3.seconds)
        yield assertTrue(true)
      }
    }

  )
