package skuber.operator.crd

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.*
import skuber.model.{CustomResource, ResourceSpecification}

/**
 * Tests for the @customResource macro annotation.
 *
 * Members (specFormat, statusFormat, crMetadata) are visible through
 * the CustomResourceDef / CustomResourceSpecDef base traits,
 * so no reflection is needed.
 */
class CustomResourceMacroSpec extends AnyFlatSpec with Matchers:

  "customResource macro" should "generate Format[Spec] for WebApp" in {
    import WebAppResource.given
    val spec = WebAppResource.Spec(3, "nginx", 80)
    val json = Json.toJson(spec)
    json.shouldBe(Json.obj(
      "replicas" -> 3,
      "image" -> "nginx",
      "port" -> 80
    ))
    json.as[WebAppResource.Spec].shouldBe(spec)
  }

  it should "generate Format[Status] for WebApp" in {
    import WebAppResource.given
    val status = WebAppResource.Status(2, true)
    val json = Json.toJson(status)
    json.shouldBe(Json.obj(
      "availableReplicas" -> 2,
      "ready" -> true
    ))
    json.as[WebAppResource.Status].shouldBe(status)
  }

  it should "generate metadata tuple for WebApp" in {
    val (kind, group, version, plural, singular, shortNames, scope) = WebAppResource.crMetadata
    kind.shouldBe("WebApp")
    group.shouldBe("test.example.com")
    version.shouldBe("v1")
    plural.shouldBe("webapps")
    singular.shouldBe("webapp")
    shortNames.shouldBe(Nil)
    scope.shouldBe("Namespaced")
  }

  "customResource macro without Status" should "generate metadata for ConfigMap2" in {
    val (kind, group, version, plural, singular, _, _) = ConfigMap2Resource.crMetadata
    kind.shouldBe("ConfigMap2")
    group.shouldBe("test.example.com")
    version.shouldBe("v1alpha1")
    plural.shouldBe("configmap2s")
    singular.shouldBe("configmap2")
  }

  it should "generate Format[Spec] for ConfigMap2" in {
    import ConfigMap2Resource.given
    val spec = ConfigMap2Resource.Spec(Map("key" -> "value"))
    val json = Json.toJson(spec)
    json.shouldBe(Json.obj("data" -> Json.obj("key" -> "value")))
    json.as[ConfigMap2Resource.Spec].shouldBe(spec)
  }

  "customResource macro" should "generate metadata for Database" in {
    val (kind, group, version, _, _, _, _) = DatabaseResource.crMetadata
    kind.shouldBe("Database")
    group.shouldBe("test.example.com")
    version.shouldBe("v1beta1")
  }

  it should "generate Format[Spec] for Database" in {
    import DatabaseResource.given
    val spec = DatabaseResource.Spec("postgres", 100)
    val json = Json.toJson(spec)
    json.shouldBe(Json.obj("engine" -> "postgres", "size" -> 100))
    json.as[DatabaseResource.Spec].shouldBe(spec)
  }

  it should "generate Format[Status] for Database" in {
    import DatabaseResource.given
    val status = DatabaseResource.Status("running")
    val json = Json.toJson(status)
    json.shouldBe(Json.obj("state" -> "running"))
    json.as[DatabaseResource.Status].shouldBe(status)
  }

  "customResource macro with explicit configuration" should "use provided values for Queue" in {
    val (kind, group, version, plural, singular, shortNames, scope) = QueueResource.crMetadata
    kind.shouldBe("Queue")
    group.shouldBe("custom.io")
    version.shouldBe("v2")
    plural.shouldBe("queues")
    singular.shouldBe("queue")
    shortNames.shouldBe(List("q", "qu"))
    scope.shouldBe("Cluster")
  }

  it should "generate Format[Spec] for Queue" in {
    import QueueResource.given
    val spec = QueueResource.Spec(1000, true)
    val json = Json.toJson(spec)
    json.shouldBe(Json.obj("capacity" -> 1000, "persistent" -> true))
    json.as[QueueResource.Spec].shouldBe(spec)
  }

  // ============ New tests for factory method and ResourceDefinition ============

  "CustomResourceDef apply method" should "create a CustomResource with correct metadata" in {
    import WebAppResource.given
    val cr = WebAppResource("my-webapp", WebAppResource.Spec(3, "nginx", 80))

    cr.kind.shouldBe("WebApp")
    cr.apiVersion.shouldBe("test.example.com/v1")
    cr.metadata.name.shouldBe("my-webapp")
    cr.spec.shouldBe(WebAppResource.Spec(3, "nginx", 80))
    cr.status.shouldBe(None)
  }

  it should "create resources that serialize to valid JSON" in {
    import WebAppResource.given
    val cr = WebAppResource("my-webapp", WebAppResource.Spec(3, "nginx", 80))
    val json = Json.toJson(cr)

    (json \ "kind").as[String].shouldBe("WebApp")
    (json \ "apiVersion").as[String].shouldBe("test.example.com/v1")
    (json \ "metadata" \ "name").as[String].shouldBe("my-webapp")
    (json \ "spec" \ "replicas").as[Int].shouldBe(3)
    (json \ "spec" \ "image").as[String].shouldBe("nginx")
    (json \ "spec" \ "port").as[Int].shouldBe(80)
  }

  it should "create resources that can be deserialized from JSON" in {
    import WebAppResource.given
    val json = Json.obj(
      "kind" -> "WebApp",
      "apiVersion" -> "test.example.com/v1",
      "metadata" -> Json.obj("name" -> "from-json"),
      "spec" -> Json.obj("replicas" -> 5, "image" -> "redis", "port" -> 6379)
    )
    val cr = json.as[WebAppResource.Resource]

    cr.kind.shouldBe("WebApp")
    cr.metadata.name.shouldBe("from-json")
    cr.spec.replicas.shouldBe(5)
    cr.spec.image.shouldBe("redis")
  }

  "CustomResourceDef resourceDefinition" should "provide correct API metadata" in {
    import WebAppResource.given
    val rd = WebAppResource.resourceDefinition

    rd.spec.names.kind.shouldBe("WebApp")
    rd.spec.group.shouldBe(Some("test.example.com"))
    rd.spec.defaultVersion.shouldBe("v1")
    rd.spec.names.plural.shouldBe("webapps")
    rd.spec.names.singular.shouldBe("webapp")
    rd.spec.scope.shouldBe(ResourceSpecification.Scope.Namespaced)
  }

  it should "use Cluster scope when configured" in {
    import QueueResource.given
    val rd = QueueResource.resourceDefinition

    rd.spec.scope.shouldBe(ResourceSpecification.Scope.Cluster)
    rd.spec.names.shortNames.shouldBe(List("q", "qu"))
  }

  "CustomResourceSpecDef (no status)" should "create resources via apply" in {
    import ConfigMap2Resource.given
    val cr = ConfigMap2Resource("my-config", ConfigMap2Resource.Spec(Map("key" -> "value")))

    cr.kind.shouldBe("ConfigMap2")
    cr.apiVersion.shouldBe("test.example.com/v1alpha1")
    cr.metadata.name.shouldBe("my-config")
    cr.spec.data.shouldBe(Map("key" -> "value"))
    cr.status.shouldBe(None)
  }

  it should "serialize spec-only resources to JSON" in {
    import ConfigMap2Resource.given
    val cr = ConfigMap2Resource("my-config", ConfigMap2Resource.Spec(Map("a" -> "b")))
    val json = Json.toJson(cr)

    (json \ "kind").as[String].shouldBe("ConfigMap2")
    (json \ "spec" \ "data" \ "a").as[String].shouldBe("b")
  }

  "CustomResource fluent API" should "work with macro-generated resources" in {
    import WebAppResource.given
    val cr = WebAppResource("my-webapp", WebAppResource.Spec(1, "nginx", 80))
      .withNamespace("production")
      .withLabels("app" -> "web", "env" -> "prod")
      .withStatus(WebAppResource.Status(1, true))

    cr.metadata.namespace.shouldBe("production")
    cr.metadata.labels.shouldBe(Map("app" -> "web", "env" -> "prod"))
    cr.status.shouldBe(Some(WebAppResource.Status(1, true)))
  }

  "customResource with user-provided format" should "use custom Spec format" in {
    import CustomFormatResource.given
    val spec = CustomFormatResource.Spec("test", 42)
    val json = Json.toJson(spec)
    // Should work with user-provided format
    json.shouldBe(Json.obj("name" -> "test", "value" -> 42))
    json.as[CustomFormatResource.Spec].shouldBe(spec)
  }

  it should "still generate Status format via macro" in {
    import CustomFormatResource.given
    val status = CustomFormatResource.Status(true)
    val json = Json.toJson(status)
    json.shouldBe(Json.obj("processed" -> true))
    json.as[CustomFormatResource.Status].shouldBe(status)
  }

  "customResource macro with enums" should "serialize and deserialize enum fields" in {
    import ReleaseResource.given
    val spec = ReleaseResource.Spec(
      name = "v1",
      phase = ReleaseResource.Phase.Active,
      risks = List(ReleaseResource.Risk.Low, ReleaseResource.Risk.High)
    )
    val json = Json.toJson(spec)
    json.shouldBe(Json.obj(
      "name" -> "v1",
      "phase" -> "Active",
      "risks" -> Json.arr("Low", "High")
    ))
    json.as[ReleaseResource.Spec].shouldBe(spec)
  }

  it should "round-trip enums in status" in {
    import ReleaseResource.given
    val status = ReleaseResource.Status(
      phase = ReleaseResource.Phase.Done,
      lastRisk = Some(ReleaseResource.Risk.Medium)
    )
    val json = Json.toJson(status)
    json.shouldBe(Json.obj(
      "phase" -> "Done",
      "lastRisk" -> "Medium"
    ))
    json.as[ReleaseResource.Status].shouldBe(status)
  }

  it should "reject unknown enum values" in {
    import ReleaseResource.given
    val json = Json.obj(
      "name" -> "v2",
      "phase" -> "InvalidPhase",
      "risks" -> Json.arr("Low")
    )
    val result = json.validate[ReleaseResource.Spec]
    result.isError shouldBe true
  }

  it should "work end-to-end with mixed formats" in {
    import CustomFormatResource.given
    val cr = CustomFormatResource("test-resource", CustomFormatResource.Spec("hello", 123))
    val json = Json.toJson(cr)

    (json \ "kind").as[String].shouldBe("CustomFormat")
    (json \ "spec" \ "name").as[String].shouldBe("hello")
    (json \ "spec" \ "value").as[Int].shouldBe(123)
  }

  // ================================================================
  // Tests for nested case classes and container types
  // ================================================================

  "customResource with nested case classes" should "generate formats for nested types" in {
    import EmployeeResource.given

    val addr = EmployeeResource.Address("123 Main St", "Boston", "02101")
    val contact = EmployeeResource.ContactInfo("alice@example.com", Some("555-1234"), List(addr))
    val spec = EmployeeResource.Spec("Alice", "Engineering", contact, Set("senior", "lead"))

    val json = Json.toJson(spec)

    (json \ "name").as[String].shouldBe("Alice")
    (json \ "contact" \ "email").as[String].shouldBe("alice@example.com")
    (json \ "contact" \ "phone").as[String].shouldBe("555-1234")
    (json \ "contact" \ "addresses")(0).as[JsObject].shouldBe(Json.obj(
      "street" -> "123 Main St",
      "city" -> "Boston",
      "zipCode" -> "02101"
    ))
    (json \ "tags").as[Set[String]].shouldBe(Set("senior", "lead"))

    // Round-trip
    json.as[EmployeeResource.Spec].shouldBe(spec)
  }

  it should "handle Option[String] in nested types" in {
    import EmployeeResource.given

    val addr = EmployeeResource.Address("456 Oak Ave", "Seattle", "98101")
    val contactWithoutPhone = EmployeeResource.ContactInfo("bob@example.com", None, List(addr))
    val spec = EmployeeResource.Spec("Bob", "Sales", contactWithoutPhone, Set.empty)

    val json = Json.toJson(spec)

    (json \ "contact" \ "phone").asOpt[String].shouldBe(None)

    // Round-trip
    json.as[EmployeeResource.Spec].shouldBe(spec)
  }

  it should "create full resource with nested types" in {
    import EmployeeResource.given

    val addr = EmployeeResource.Address("789 Pine Rd", "Denver", "80202")
    val contact = EmployeeResource.ContactInfo("carol@example.com", Some("555-9999"), List(addr))
    val cr = EmployeeResource(
      "carol",
      EmployeeResource.Spec("Carol", "Marketing", contact, Set("manager"))
    ).withStatus(EmployeeResource.Status(true, "2024-01-15"))

    val json = Json.toJson(cr)

    (json \ "kind").as[String].shouldBe("Employee")
    ((json \ "spec" \ "contact" \ "addresses")(0) \ "city").as[String].shouldBe("Denver")
    (json \ "status" \ "active").as[Boolean].shouldBe(true)
  }

  "customResource with deeply nested containers" should "handle List[Option[T]]" in {
    import ProjectResource.given

    val milestone = ProjectResource.Milestone("M1", completed = false)
    val task1 = ProjectResource.Task(1, "Task 1", Some(milestone))
    val task2 = ProjectResource.Task(2, "Task 2", None)

    val spec = ProjectResource.Spec(
      name = "Project X",
      tasks = List(task1, task2),
      optionalTasks = List(Some(task1), None, Some(task2)),
      tasksByCategory = Map.empty
    )

    val json = Json.toJson(spec)

    (json \ "tasks").as[List[JsValue]].size.shouldBe(2)
    (json \ "optionalTasks").as[List[JsValue]].size.shouldBe(3)
    ((json \ "optionalTasks")(0) \ "id").as[Int].shouldBe(1)
    (json \ "optionalTasks")(1).shouldBe(JsNull)

    // Round-trip
    json.as[ProjectResource.Spec].shouldBe(spec)
  }

  it should "handle Map[String, List[T]]" in {
    import ProjectResource.given

    val task1 = ProjectResource.Task(1, "Design", None)
    val task2 = ProjectResource.Task(2, "Implement", None)
    val task3 = ProjectResource.Task(3, "Test", None)

    val spec = ProjectResource.Spec(
      name = "Project Y",
      tasks = Nil,
      optionalTasks = Nil,
      tasksByCategory = Map(
        "frontend" -> List(task1, task2),
        "backend" -> List(task3)
      )
    )

    val json = Json.toJson(spec)

    (json \ "tasksByCategory" \ "frontend").as[List[JsValue]].size.shouldBe(2)
    (json \ "tasksByCategory" \ "backend").as[List[JsValue]].size.shouldBe(1)
    ((json \ "tasksByCategory" \ "frontend")(0) \ "description").as[String].shouldBe("Design")

    // Round-trip
    json.as[ProjectResource.Spec].shouldBe(spec)
  }

  it should "handle nested case class with Option field" in {
    import ProjectResource.given

    val milestone = ProjectResource.Milestone("Release 1.0", completed = true)
    val task = ProjectResource.Task(1, "Ship it", Some(milestone))

    val spec = ProjectResource.Spec("Release Project", List(task), Nil, Map.empty)
    val json = Json.toJson(spec)

    ((json \ "tasks")(0) \ "milestone" \ "name").as[String].shouldBe("Release 1.0")
    ((json \ "tasks")(0) \ "milestone" \ "completed").as[Boolean].shouldBe(true)

    // Round-trip
    json.as[ProjectResource.Spec].shouldBe(spec)
  }
