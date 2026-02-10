package skuber.examples.kronjob

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{Json, JsSuccess, Format}

/**
 * Unit tests for KronJobResource JSON serialization.
 *
 * Note: The @customResource macro generates formats that require all fields
 * to be present in JSON (default values are not automatically applied for
 * missing fields). Tests use complete JSON with all fields specified.
 */
class KronJobResourceSpec extends AnyFlatSpec with Matchers:

  import KronJobResource.given

  "KronJob Spec format" should "serialize spec to JSON" in {
    val spec = KronJobResource.Spec(
      schedule = "0 0 * * *",
      jobTemplate = KronJobResource.JobTemplateSpec(
        image = "nginx",
        command = List("nginx", "-g", "daemon off;")
      )
    )

    val json = Json.toJson(spec)

    (json \ "schedule").as[String] shouldBe "0 0 * * *"
    (json \ "jobTemplate" \ "image").as[String] shouldBe "nginx"
    (json \ "jobTemplate" \ "command").as[List[String]] shouldBe List("nginx", "-g", "daemon off;")
  }

  it should "round-trip spec through JSON" in {
    val original = KronJobResource.Spec(
      schedule = "*/5 * * * *",
      jobTemplate = KronJobResource.JobTemplateSpec(
        image = "busybox",
        command = List("echo", "hello"),
        args = List("world"),
        restartPolicy = "Never"
      ),
      startingDeadlineSeconds = Some(300),
      concurrencyPolicy = KronJobResource.ConcurrencyPolicy.Replace,
      suspend = true,
      successfulJobsHistoryLimit = 5,
      failedJobsHistoryLimit = 2
    )

    val json = Json.toJson(original)
    val reparsed = json.as[KronJobResource.Spec]

    reparsed shouldBe original
  }

  it should "parse spec from complete JSON" in {
    val specJson =
      """{
        |  "schedule": "*/5 * * * *",
        |  "jobTemplate": {
        |    "image": "busybox",
        |    "command": ["echo", "Hello from KronJob!"],
        |    "args": [],
        |    "restartPolicy": "OnFailure"
        |  },
        |  "startingDeadlineSeconds": null,
        |  "concurrencyPolicy": "Allow",
        |  "suspend": false,
        |  "successfulJobsHistoryLimit": 3,
        |  "failedJobsHistoryLimit": 1
        |}""".stripMargin

    val json = Json.parse(specJson)
    val result = json.validate[KronJobResource.Spec]

    result shouldBe a[JsSuccess[?]]
    val spec = result.get

    spec.schedule shouldBe "*/5 * * * *"
    spec.jobTemplate.image shouldBe "busybox"
    spec.jobTemplate.command shouldBe List("echo", "Hello from KronJob!")
    spec.concurrencyPolicy shouldBe KronJobResource.ConcurrencyPolicy.Allow
    spec.successfulJobsHistoryLimit shouldBe 3
    spec.failedJobsHistoryLimit shouldBe 1
  }

  it should "parse spec with missing optional fields using defaults" in {
    // Only required fields: schedule and jobTemplate (with just image)
    val minimalSpecJson =
      """{
        |  "schedule": "*/5 * * * *",
        |  "jobTemplate": {
        |    "image": "busybox"
        |  }
        |}""".stripMargin

    val json = Json.parse(minimalSpecJson)
    val result = json.validate[KronJobResource.Spec]

    result shouldBe a[JsSuccess[?]]
    val spec = result.get

    // Explicitly provided
    spec.schedule shouldBe "*/5 * * * *"
    spec.jobTemplate.image shouldBe "busybox"

    // Should use default values
    spec.jobTemplate.command shouldBe Nil
    spec.jobTemplate.args shouldBe Nil
    spec.jobTemplate.restartPolicy shouldBe "OnFailure"
    spec.startingDeadlineSeconds shouldBe None
    spec.concurrencyPolicy shouldBe KronJobResource.ConcurrencyPolicy.Allow
    spec.suspend shouldBe false
    spec.successfulJobsHistoryLimit shouldBe 3
    spec.failedJobsHistoryLimit shouldBe 1
  }

  it should "parse all concurrency policies" in {
    def parseWithPolicy(policy: String): KronJobResource.Spec =
      val specJson =
        s"""{
           |  "schedule": "0 * * * *",
           |  "jobTemplate": {"image": "alpine", "command": [], "args": [], "restartPolicy": "OnFailure"},
           |  "startingDeadlineSeconds": null,
           |  "concurrencyPolicy": "$policy",
           |  "suspend": false,
           |  "successfulJobsHistoryLimit": 3,
           |  "failedJobsHistoryLimit": 1
           |}""".stripMargin
      Json.parse(specJson).as[KronJobResource.Spec]

    parseWithPolicy("Allow").concurrencyPolicy shouldBe KronJobResource.ConcurrencyPolicy.Allow
    parseWithPolicy("Forbid").concurrencyPolicy shouldBe KronJobResource.ConcurrencyPolicy.Forbid
    parseWithPolicy("Replace").concurrencyPolicy shouldBe KronJobResource.ConcurrencyPolicy.Replace
  }

  "KronJob Status format" should "serialize status to JSON" in {
    import play.api.libs.json.JsArray

    val status = KronJobResource.Status(
      active = Nil,
      lastScheduleTime = None,
      lastSuccessfulTime = None
    )

    val json = Json.toJson(status)

    (json \ "active").get shouldBe JsArray.empty
  }

  it should "round-trip status through JSON" in {
    val original = KronJobResource.Status(
      active = Nil,
      lastScheduleTime = None,
      lastSuccessfulTime = None
    )

    val json = Json.toJson(original)
    val reparsed = json.as[KronJobResource.Status]

    reparsed shouldBe original
  }

  it should "serialize ZonedDateTime in ISO 8601 format for Kubernetes" in {
    import java.time.{ZoneId, ZonedDateTime}

    val dt = ZonedDateTime.of(2026, 2, 10, 15, 30, 0, 0, ZoneId.of("Europe/London"))
    val status = KronJobResource.Status(
      active = Nil,
      lastScheduleTime = Some(dt),
      lastSuccessfulTime = None
    )

    val json = Json.toJson(status)
    val lastScheduleTimeStr = (json \ "lastScheduleTime").as[String]

    // Should be ISO 8601 format without zone name, in UTC
    lastScheduleTimeStr shouldBe "2026-02-10T15:30:00Z"
    // Should NOT contain the zone ID in brackets
    lastScheduleTimeStr should not include "[Europe/London]"
  }

  "KronJob factory method" should "create resource with correct metadata" in {
    val spec = KronJobResource.Spec(
      schedule = "0 * * * *",
      jobTemplate = KronJobResource.JobTemplateSpec(image = "nginx")
    )

    val cronJob = KronJobResource("my-kronjob", spec)

    cronJob.kind shouldBe "KronJob"
    cronJob.apiVersion shouldBe "batch.tutorial.skuber.io/v1"
    cronJob.metadata.name shouldBe "my-kronjob"
    cronJob.spec shouldBe spec
    cronJob.status shouldBe None
  }

  it should "serialize full KronJob resource to JSON" in {
    val spec = KronJobResource.Spec(
      schedule = "0 0 * * *",
      jobTemplate = KronJobResource.JobTemplateSpec(
        image = "backup-tool",
        command = List("/backup.sh")
      )
    )

    val kronJob = KronJobResource("daily-backup", spec)
    val json = Json.toJson(kronJob)

    (json \ "kind").as[String] shouldBe "KronJob"
    (json \ "apiVersion").as[String] shouldBe "batch.tutorial.skuber.io/v1"
    (json \ "metadata" \ "name").as[String] shouldBe "daily-backup"
    (json \ "spec" \ "schedule").as[String] shouldBe "0 0 * * *"
    (json \ "spec" \ "jobTemplate" \ "image").as[String] shouldBe "backup-tool"
  }
