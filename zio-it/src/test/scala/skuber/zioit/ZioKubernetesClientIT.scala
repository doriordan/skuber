package skuber.zioit

import zio.*
import zio.test.*
import skuber.zio.*
import skuber.model.*
import skuber.json.format.*

object ZioKubernetesClientIT extends ZIOSpecDefault:

  // Skip all tests if SKUBER_K8S_TEST is not set
  private val requireCluster: ZIO[Any, TestFailure[Nothing], Unit] =
    ZIO.fromOption(sys.env.get("SKUBER_K8S_TEST"))
      .mapError(_ => TestFailure.fail("Set SKUBER_K8S_TEST=true to run integration tests"))
      .unit

  def spec = suite("ZIO Kubernetes Client Integration")(
    test("list pods in default namespace") {
      for
        _    <- requireCluster
        pods <- ZIO.serviceWithZIO[ZKubernetesClient](_.list[PodList]())
      yield assertTrue(pods.items.length >= 0)
    },
    test("create, get, and delete a ConfigMap") {
      for
        _       <- requireCluster
        client  <- ZIO.service[ZKubernetesClient]
        cm       = ConfigMap(metadata = ObjectMeta(name = "zio-test-cm", namespace = "default"))
        created <- client.create(cm)
        fetched <- client.get[ConfigMap]("zio-test-cm")
        _       <- client.delete[ConfigMap]("zio-test-cm")
      yield assertTrue(fetched.name == "zio-test-cm")
    }
  ).provideLayerShared(ZKubernetesClient.layer)
