package skuber.catseffect

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import munit.CatsEffectSuite
import skuber.api.client.*
import skuber.model.*
import skuber.json.format.*

class CatsIntegrationSpec extends CatsEffectSuite:

  given LoggingContext = RequestLoggingContext()

  val clientResource = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  clientResource.test("get namespace list") { client =>
    client.list[NamespaceList]().map { result =>
      assert(result.isRight, s"Expected Right but got $result")
      val namespaces = result.toOption.get
      assert(namespaces.items.exists(_.name == "default"))
    }
  }

  clientResource.test("create and delete a pod") { client =>
    val container = Container(name = "test", image = "busybox", command = List("sleep", "30"))
    val pod = Pod(metadata = ObjectMeta(name = "cats-test-pod", namespace = "default"),
      spec = Some(Pod.Spec(containers = List(container))))

    for
      created <- client.create[Pod](pod)
      _ = assert(created.isRight, s"Create failed: $created")
      fetched <- client.get[Pod]("cats-test-pod")
      _ = assert(fetched.isRight, s"Get failed: $fetched")
      _ = assertEquals(fetched.toOption.get.name, "cats-test-pod")
      deleted <- client.delete[Pod]("cats-test-pod")
      _ = assert(deleted.isRight, s"Delete failed: $deleted")
    yield ()
  }
