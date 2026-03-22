package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import skuber.json.format.*
import skuber.model.*
import skuber.zio.ZKubernetesClient

object ZioKubernetesClientIT:

  def spec = suite("ZIO Kubernetes Client Integration")(

    test("list pods in default namespace") {
      ZIO.serviceWithZIO[ZKubernetesClient](_.list[PodList]())
        .map(pods => assertTrue(pods.items.length >= 0))
    },

    test("create, get, and delete a ConfigMap") {
      ZIO.serviceWithZIO[ZKubernetesClient] { client =>
        val cm = ConfigMap(metadata = ObjectMeta(name = "zio-test-cm", namespace = "default"))
        for
          created <- client.create(cm)
          fetched <- client.get[ConfigMap]("zio-test-cm")
          _       <- client.delete[ConfigMap]("zio-test-cm")
        yield assertTrue(fetched.name == "zio-test-cm")
      }
    }

  )
