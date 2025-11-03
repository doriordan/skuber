package skuber.model.batch

import java.time.ZonedDateTime.parse

import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.model.Pod.Template
import skuber.model.{Container, RestartPolicy}
import skuber.json.batch.format._
import skuber.model.Pod

/**
  * @author Cory Klein
  */
class JobSpec extends Specification {
  "This is a unit specification for the skuber Job class. ".txt

  "A Job object can be constructed from a name and a pod template" >> {
    val container = Container(name = "jobcontainer", image = "jobimage")
    val template = Pod.Template.Spec.named("jobtemplatespecname").addContainer(container)
    val job = Job("jobname").withTemplate(template)

    job.name must beEqualTo("jobname")
    job.spec.get.template.get must beEqualTo(template)
  }

  "A Job object can be constructed with fluent API" >> {
    val job = Job("jobname")
      .withActiveDeadlineSeconds(42)
      .withBackoffLimit(43)
      .withCompletions(44)
      .withParallelism(45)

    job.spec.get.activeDeadlineSeconds must beEqualTo(Some(42))
    job.spec.get.backoffLimit must beEqualTo(Some(43))
    job.spec.get.completions must beEqualTo(Some(44))
    job.spec.get.parallelism must beEqualTo(Some(45))
  }


  "A Job object can be written to Json and then read back again successfully" >> {
    val container = Container(name = "jobcontainer", image = "jobimage")
    val template = Pod.Template.Spec.named("jobtemplatespecname").addContainer(container)
    val job = Job("jobname").withTemplate(template)

    val readJob = Json.fromJson[Job](Json.toJson(job)).get
    readJob must beEqualTo(job)
  }

  "A Job object can be read directly from a JSON string" >> {
    val jobJsonStr =
      """
        |{
        |	"apiVersion": "batch/v1",
        |	"kind": "Job",
        |	"metadata": {
        |		"name": "pi"
        |	},
        |	"spec": {
        |		"template": {
        |			"metadata": {
        |				"name": "templatename"
        |			},
        |			"spec": {
        |				"containers": [
        |					{
        |						"name": "containername",
        |						"image": "perl",
        |						"command": [
        |							"perl",
        |							"-Mbignum=bpi",
        |							"-wle",
        |							"print bpi(2000)"
        |						]
        |					}
        |				],
        |				"restartPolicy": "Never"
        |			}
        |		},
        |  "backoffLimit": 4,
        |  "activeDeadlineSeconds": 60
        |	},
        | "status": {
        |    "conditions": [
        |      {
        |        "type": "Failed",
        |        "status": "True",
        |        "lastProbeTime": "2019-02-01T11:43:05Z",
        |        "lastTransitionTime": "2019-02-01T11:43:05Z",
        |        "reason": "BackoffLimitExceeded",
        |        "message": "Job has reached the specified backoff limit"
        |      }
        |    ],
        |    "startTime": "2019-02-01T11:42:19Z",
        |    "active": 1,
        |    "succeeded": 2,
        |    "failed": 3
        |  }
        |}
      """.stripMargin

    val job = Json.parse(jobJsonStr).as[Job]
    job.kind must beEqualTo("Job")
    job.name must beEqualTo("pi")
    job.spec.get.activeDeadlineSeconds must beEqualTo(Some(60))
    job.spec.get.backoffLimit must beEqualTo(Some(4))
    val templateSpec: Template.Spec = job.spec.get.template.get
    templateSpec.metadata.name must beEqualTo("templatename")
    templateSpec.spec.get.restartPolicy must beEqualTo(RestartPolicy.Never)
    val container = templateSpec.spec.get.containers.head
    container.name must beEqualTo("containername")
    container.image must beEqualTo("perl")
    val status = job.status.get
    status.active must beEqualTo(Some(1))
    status.succeeded must beEqualTo(Some(2))
    status.failed must beEqualTo(Some(3))
    status.startTime must beEqualTo(Some(parse("2019-02-01T11:42:19Z")))
    val conditions = status.conditions.head
    conditions.`type` must beEqualTo("Failed")
    conditions.status must beEqualTo("True")
    conditions.lastProbeTime must beEqualTo(Some(parse("2019-02-01T11:43:05Z")))
    conditions.lastTransitionTime must beEqualTo(Some(parse("2019-02-01T11:43:05Z")))
    conditions.reason must beEqualTo(Some("BackoffLimitExceeded"))
    conditions.message must beEqualTo(Some("Job has reached the specified backoff limit"))
  }
}
