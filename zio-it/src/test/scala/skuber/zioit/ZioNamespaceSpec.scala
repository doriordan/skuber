package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import skuber.json.format.*
import skuber.model.{Namespace, Pod}
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

object ZioNamespaceSpec:

  def spec = suite("ZIO Namespace")(

    test("namespace isolation lifecycle") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val ns1 = "zio-test-ns1-" + java.util.UUID.randomUUID().toString.take(8)
        val ns2 = "zio-test-ns2-" + java.util.UUID.randomUUID().toString.take(8)
        val pod1Name = java.util.UUID.randomUUID().toString
        val pod2Name = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(Namespace(ns1))
          _ <- k8s.create(Namespace(ns2))
          p1 <- k8s.usingNamespace(ns1).create(getNginxPodWithNamespace(ns1, pod1Name))
          _ = assert(p1.name == pod1Name && p1.metadata.namespace == ns1)
          // object namespace (ns2) wins over client namespace (ns1)
          p2 <- k8s.usingNamespace(ns1).create(getNginxPodWithNamespace(ns2, pod2Name))
          _ = assert(p2.name == pod2Name && p2.metadata.namespace == ns2)
          found2 <- k8s.usingNamespace(ns2).get[Pod](pod2Name)
          _ = assert(found2.name == pod2Name)
          // pod1 not found in default namespace
          notFound <- k8s.getOption[Pod](pod1Name)
          _ = assert(notFound.isEmpty)
          found1 <- k8s.usingNamespace(ns1).get[Pod](pod1Name)
          _ = assert(found1.name == pod1Name)
          _ <- k8s.usingNamespace(ns1).delete[Pod](pod1Name)
          _ <- retryUntilGone(k8s.usingNamespace(ns1).get[Pod](pod1Name), retries = 30, delay = 3.seconds)
          _ <- k8s.usingNamespace(ns2).delete[Pod](pod2Name)
          _ <- retryUntilGone(k8s.usingNamespace(ns2).get[Pod](pod2Name), retries = 30, delay = 3.seconds)
          _ <- k8s.delete[Namespace](ns1)
          _ <- retryUntilGone(k8s.get[Namespace](ns1), retries = 120, delay = 5.seconds)
          _ <- k8s.delete[Namespace](ns2)
          _ <- retryUntilGone(k8s.get[Namespace](ns2), retries = 120, delay = 5.seconds)
        yield assertTrue(true)
      }
    }

  )
