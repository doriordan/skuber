package skuber.model.ac.batch

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import skuber.model._
import skuber.model.ac._
import skuber.model.batch.Job
import skuber.json.format._

class JobApplyConfigSpec extends AnyFlatSpec with Matchers {

  "JobApplyConfig" should "be constructed by name" in {
    val job = JobApplyConfig("my-job")
    job.name shouldBe "my-job"
    job.kind shouldBe "Job"
    job.apiVersion shouldBe "batch/v1"
  }

  it should "serialize with spec fields" in {
    val job = JobApplyConfig("my-job")
      .withSpec(JobSpecApplyConfig()
        .withParallelism(2)
        .withCompletions(5)
        .withBackoffLimit(3)
        .withTemplate(PodTemplateSpecApplyConfig()
          .addContainer(ContainerApplyConfig("worker", "worker:latest"))
          .withRestartPolicy(RestartPolicy.Never)
        )
      )
    val json = Json.toJson(job)
    (json \ "kind").as[String] shouldBe "Job"
    (json \ "apiVersion").as[String] shouldBe "batch/v1"
    (json \ "spec" \ "parallelism").as[Int] shouldBe 2
    (json \ "spec" \ "completions").as[Int] shouldBe 5
    (json \ "spec" \ "backoffLimit").as[Int] shouldBe 3
  }

  it should "extend ApplyConfiguration[Job]" in {
    val job: ApplyConfiguration[Job] = JobApplyConfig("my-job")
    job.name shouldBe "my-job"
  }

  it should "support ttlSecondsAfterFinished" in {
    val spec = JobSpecApplyConfig().withTTLSecondsAfterFinished(300)
    spec.ttlSecondsAfterFinished shouldBe Some(300)
  }
}
