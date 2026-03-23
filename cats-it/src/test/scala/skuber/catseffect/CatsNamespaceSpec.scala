package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import skuber.api.client.{LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.{Namespace, Pod}
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*

class CatsNamespaceSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 40.minutes // namespace deletion is slow

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  client.test("namespace isolation lifecycle") { k8s =>
    val ns1 = "cats-test-ns1-" + java.util.UUID.randomUUID().toString.take(8)
    val ns2 = "cats-test-ns2-" + java.util.UUID.randomUUID().toString.take(8)
    val pod1Name = java.util.UUID.randomUUID().toString
    val pod2Name = java.util.UUID.randomUUID().toString
    for
      // Create namespaces
      _ <- k8s.create(Namespace(ns1)).map(_.getOrElse(fail("Create ns1 failed")))
      _ <- k8s.create(Namespace(ns2)).map(_.getOrElse(fail("Create ns2 failed")))
      // Create pod1 in ns1
      p1 <- k8s.usingNamespace(ns1).create(getNginxPodWithNamespace(ns1, pod1Name))
        .map(_.getOrElse(fail("Create pod1 failed")))
      _ = assertEquals(p1.name, pod1Name)
      _ = assertEquals(p1.metadata.namespace, ns1)
      // Create pod2: object namespace (ns2) wins over client namespace (ns1)
      p2 <- k8s.usingNamespace(ns1).create(getNginxPodWithNamespace(ns2, pod2Name))
        .map(_.getOrElse(fail("Create pod2 failed")))
      _ = assertEquals(p2.name, pod2Name)
      _ = assertEquals(p2.metadata.namespace, ns2)
      // Find pod2 in ns2
      found2 <- k8s.usingNamespace(ns2).get[Pod](pod2Name)
        .map(_.getOrElse(fail("Get pod2 from ns2 failed")))
      _ = assertEquals(found2.name, pod2Name)
      // pod1 not found in default namespace
      notFound <- k8s.get[Pod](pod1Name)
      _ = assert(notFound.isLeft, s"Expected pod1 not in default ns, got: $notFound")
      _ = assert(notFound.left.toOption.get.isNotFound)
      // Find pod1 in ns1
      found1 <- k8s.usingNamespace(ns1).get[Pod](pod1Name)
        .map(_.getOrElse(fail("Get pod1 from ns1 failed")))
      _ = assertEquals(found1.name, pod1Name)
      // Cleanup pods
      _ <- k8s.usingNamespace(ns1).delete[Pod](pod1Name)
      _ <- retryUntilGone(k8s.usingNamespace(ns1).get[Pod](pod1Name), retries = 30, delay = 3.seconds)
      _ <- k8s.usingNamespace(ns2).delete[Pod](pod2Name)
      _ <- retryUntilGone(k8s.usingNamespace(ns2).get[Pod](pod2Name), retries = 30, delay = 3.seconds)
      // Delete namespaces
      _ <- k8s.delete[Namespace](ns1)
      _ <- retryUntilGone(k8s.get[Namespace](ns1), retries = 120, delay = 5.seconds)
      _ <- k8s.delete[Namespace](ns2)
      _ <- retryUntilGone(k8s.get[Namespace](ns2), retries = 120, delay = 5.seconds)
    yield ()
  }
