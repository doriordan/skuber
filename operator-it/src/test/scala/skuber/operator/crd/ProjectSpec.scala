package skuber.operator.crd

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.*
import play.api.libs.json.Json
import skuber.model.ListResource
import skuber.model.apiextensions.v1.CustomResourceDefinition

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

/**
 * Abstract integration test for the Project custom resource.
 *
 * Creates the Project CRD before all tests and deletes it after all tests.
 * Tests CRUD operations on Project resource instances.
 */
abstract class ProjectSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll {

  import Project.given

  val testResourceName = "test-project"
  val testNamespace = "default"

  override def beforeAll(): Unit = {
    super.beforeAll()
    val k8s = createK8sClient(config)
    try {
      // Clean up any existing CRD from previous test runs
      try {
        Await.ready(k8s.delete[CustomResourceDefinition](ProjectCRDFixture.crdName), 10.seconds)
        Thread.sleep(2000) // Wait for CRD deletion to propagate
      } catch {
        case _: Exception => // CRD doesn't exist, that's fine
      }

      // Create the CRD
      Await.ready(k8s.create(ProjectCRDFixture.crd), 10.seconds)
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
        Await.ready(k8s.delete[Project](testResourceName), 5.seconds)
      } catch {
        case _: Exception => // Resource doesn't exist
      }

      // Clean up CRD
      try {
        Await.ready(k8s.delete[CustomResourceDefinition](ProjectCRDFixture.crdName), 10.seconds)
      } catch {
        case _: Exception => // CRD doesn't exist
      }
    } finally {
      k8s.close()
    }
    super.afterAll()
  }

  behavior of "Project custom resource"

  private val baseSpec = Project.Spec(
    name = "alpha",
    owner = Project.Owner("alice", "platform"),
    repositories = List(
      Project.Repo("https://example.com/alpha.git", "scala"),
      Project.Repo("https://example.com/alpha-ui.git", "typescript")
    ),
    labels = Map("tier" -> "backend", "priority" -> "high"),
    settings = Project.Settings(
      retentionDays = 30,
      features = Set("audit", "metrics"),
      limits = Project.Limits(cpu = 2, memoryMi = 2048)
    ),
    alerts = Some(List(Project.Alert("slack", "#ops"), Project.Alert("email", "dev@acme.io")))
  )

  it should "create a Project using the macro-generated factory method" in {
    withK8sClient { k8s =>
      val project = Project(testResourceName, baseSpec)

      k8s.create(project).map { created =>
        created.name shouldBe testResourceName
        created.kind shouldBe "Project"
        created.apiVersion shouldBe "test.skuber.io/v1"
        created.spec.name shouldBe "alpha"
        created.spec.owner.team shouldBe "platform"
        created.spec.repositories.size shouldBe 2
        created.status shouldBe None
      }
    }
  }

  it should "get a Project using the macro-generated ResourceDefinition" in {
    withK8sClient { k8s =>
      k8s.get[Project](testResourceName).map { retrieved =>
        retrieved.name shouldBe testResourceName
        retrieved.spec.name shouldBe "alpha"
        retrieved.spec.settings.limits.cpu shouldBe 2
      }
    }
  }

  it should "list Project resources" in {
    withK8sClient { k8s =>
      k8s.list[ListResource[Project]]().map { list =>
        list.items should not be empty
        list.items.exists(_.name == testResourceName) shouldBe true
      }
    }
  }

  it should "update a Project" in {
    withK8sClient { k8s =>
      for {
        existing <- k8s.get[Project](testResourceName)
        updated = existing.copy(spec = existing.spec.copy(
          settings = existing.spec.settings.copy(
            retentionDays = 60,
            features = existing.spec.settings.features + "alerts"
          ),
          labels = existing.spec.labels + ("priority" -> "critical")
        ))
        result <- k8s.update(updated)
      } yield {
        result.spec.settings.retentionDays shouldBe 60
        result.spec.settings.features.contains("alerts") shouldBe true
        result.spec.labels("priority") shouldBe "critical"
      }
    }
  }

  it should "serialize and deserialize Project correctly" in {
    withK8sClient { k8s =>
      k8s.get[Project](testResourceName).map { retrieved =>
        val json = Json.toJson(retrieved)

        (json \ "kind").as[String] shouldBe "Project"
        (json \ "apiVersion").as[String] shouldBe "test.skuber.io/v1"
        (json \ "spec" \ "owner" \ "name").as[String] shouldBe "alice"
        (json \ "spec" \ "settings" \ "limits" \ "memoryMi").as[Int] shouldBe 2048

        // Test round-trip
        val parsed = json.as[Project]
        parsed.name shouldBe testResourceName
        parsed.spec.repositories.head.language shouldBe "scala"
      }
    }
  }

  it should "delete a Project" in {
    withK8sClient { k8s =>
      k8s.delete[Project](testResourceName).map { _ =>
        succeed
      }
    }
  }

  it should "verify Project was deleted" in {
    withK8sClient { k8s =>
      k8s.get[Project](testResourceName).failed.map { ex =>
        ex.getMessage should include("404")
      }
    }
  }
}
