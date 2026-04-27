package skuber.model.ac.batch

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.batch.CronJob
import skuber.json.format._

class CronJobApplyConfigSpec extends AnyFlatSpec with Matchers {

  "CronJobApplyConfig" should "be constructed by name" in {
    val cj = CronJobApplyConfig("my-cronjob")
    cj.name shouldBe "my-cronjob"
    cj.kind shouldBe "CronJob"
    cj.apiVersion shouldBe "batch/v1beta1"
  }

  it should "serialize with spec fields" in {
    val cj = CronJobApplyConfig("my-cronjob")
      .withSpec(CronJobSpecApplyConfig()
        .withSchedule("*/5 * * * *")
        .withJobTemplate(JobTemplateSpecApplyConfig()
          .withSpec(JobSpecApplyConfig()
            .withTemplate(PodTemplateSpecApplyConfig()
              .addContainer(ContainerApplyConfig("worker", "worker:latest"))
              .withRestartPolicy(RestartPolicy.Never)
            )
          )
        )
        .withConcurrencyPolicy("Forbid")
      )
    val json = Json.toJson(cj)
    (json \ "kind").as[String] shouldBe "CronJob"
    (json \ "spec" \ "schedule").as[String] shouldBe "*/5 * * * *"
    (json \ "spec" \ "concurrencyPolicy").as[String] shouldBe "Forbid"
  }

  it should "extend ApplyConfiguration[CronJob]" in {
    val cj: ApplyConfiguration[CronJob] = CronJobApplyConfig("my-cronjob")
    cj.name shouldBe "my-cronjob"
  }

  it should "support suspend and history limits" in {
    val spec = CronJobSpecApplyConfig()
      .withSuspend(true)
      .withSuccessfulJobsHistoryLimit(3)
      .withFailedJobsHistoryLimit(1)
    spec.suspend shouldBe Some(true)
    spec.successfulJobsHistoryLimit shouldBe Some(3)
    spec.failedJobsHistoryLimit shouldBe Some(1)
  }
}
