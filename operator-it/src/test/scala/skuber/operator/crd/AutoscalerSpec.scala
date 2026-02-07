package skuber.operator.crd

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.*
import play.api.libs.json.Json
import skuber.api.client.KubernetesClient
import skuber.model.*
import skuber.model.apiextensions.v1.CustomResourceDefinition

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

/**
 * Abstract integration test for the Autoscaler custom resource.
 *
 * Creates the Autoscaler CRD before all tests and deletes it after all tests.
 * Tests CRUD operations on Autoscaler resource instances.
 */
abstract class AutoscalerSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll {

  import Autoscaler.given

  val testResourceName = "test-autoscaler"
  val testNamespace = "default"

  override def beforeAll(): Unit = {
    super.beforeAll()
    val k8s = createK8sClient(config)
    try {
      // Clean up any existing CRD from previous test runs
      try {
        Await.ready(k8s.delete[CustomResourceDefinition](AutoscalerCRDFixture.crdName), 10.seconds)
        Thread.sleep(2000) // Wait for CRD deletion to propagate
      } catch {
        case _: Exception => // CRD doesn't exist, that's fine
      }

      // Create the CRD
      Await.ready(k8s.create(AutoscalerCRDFixture.crd), 10.seconds)
      Thread.sleep(3000) // Wait for CRD to be established
    } finally {
      k8s.close()
    }
  }

  override def afterAll(): Unit = {
    val k8s = createK8sClient(config)
    try {
      // Clean up test resources
      try {
        Await.ready(k8s.delete[Autoscaler.Resource](testResourceName), 5.seconds)
      } catch {
        case _: Exception => // Resource doesn't exist
      }

      // Clean up CRD
      try {
        Await.ready(k8s.delete[CustomResourceDefinition](AutoscalerCRDFixture.crdName), 10.seconds)
      } catch {
        case _: Exception => // CRD doesn't exist
      }
    } finally {
      k8s.close()
    }
    super.afterAll()
  }

  behavior of "Autoscaler custom resource"

  it should "create an Autoscaler using the macro-generated factory method" in {
    withK8sClient { k8s =>
      val autoscaler = Autoscaler(testResourceName, Autoscaler.Spec(3, "nginx:latest"))

      k8s.create(autoscaler).map { created =>
        created.name shouldBe testResourceName
        created.kind shouldBe "Autoscaler"
        created.apiVersion shouldBe "test.skuber.io/v1"
        created.spec.desiredReplicas shouldBe 3
        created.spec.image shouldBe "nginx:latest"
        created.status shouldBe None
      }
    }
  }

  it should "get an Autoscaler using the macro-generated ResourceDefinition" in {
    withK8sClient { k8s =>
      k8s.get[Autoscaler](testResourceName).map { retrieved =>
        retrieved.name shouldBe testResourceName
        retrieved.spec.desiredReplicas shouldBe 3
        retrieved.spec.image shouldBe "nginx:latest"
      }
    }
  }

  it should "list Autoscaler resources" in {
    withK8sClient { k8s =>
      k8s.list[ListResource[Autoscaler.Resource]]().map { list =>
        list.items should not be empty
        list.items.exists(_.name == testResourceName) shouldBe true
      }
    }
  }

  it should "update an Autoscaler" in {
    withK8sClient { k8s =>
      for {
        existing <- k8s.get[Autoscaler.Resource](testResourceName)
        updated = existing.copy(spec = Autoscaler.Spec(5, "nginx:1.25"))
        result <- k8s.update(updated)
      } yield {
        result.spec.desiredReplicas shouldBe 5
        result.spec.image shouldBe "nginx:1.25"
      }
    }
  }

  it should "serialize and deserialize Autoscaler correctly" in {
    withK8sClient { k8s =>
      k8s.get[Autoscaler.Resource](testResourceName).map { retrieved =>
        val json = Json.toJson(retrieved)

        (json \ "kind").as[String] shouldBe "Autoscaler"
        (json \ "apiVersion").as[String] shouldBe "test.skuber.io/v1"
        (json \ "spec" \ "desiredReplicas").as[Int] shouldBe 5
        (json \ "spec" \ "image").as[String] shouldBe "nginx:1.25"

        // Test round-trip
        val parsed = json.as[Autoscaler.Resource]
        parsed.name shouldBe testResourceName
        parsed.spec.desiredReplicas shouldBe 5
      }
    }
  }

  it should "delete an Autoscaler" in {
    withK8sClient { k8s =>
      k8s.delete[Autoscaler.Resource](testResourceName).map { _ =>
        succeed
      }
    }
  }

  it should "verify Autoscaler was deleted" in {
    withK8sClient { k8s =>
      k8s.get[Autoscaler.Resource](testResourceName).failed.map { ex =>
        ex.getMessage should include("404")
      }
    }
  }
}

