package skuber.batch

import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.Pod.Template
import skuber.{Container, Pod, RestartPolicy}
import skuber.json.batch.format._
/**
  * @author Cory Klein
  */
class JobSpec extends Specification {
  "This is a unit specification for the skuber Job class. ".txt

  "A Job object can be constructed from a name and a pod template" >> {
    val container = Container(name = "jobcontainer", image = "jobimage")
    val template = Pod.Template.Spec.named("jobtemplatespecname").addContainer(container)
    val job = Job("jobname").withTemplate(template)

    job.name mustEqual "jobname"
    job.spec.get.template.get mustEqual template
  }

  "A Job object can be written to Json and then read back again successfully" >> {
    val container = Container(name = "jobcontainer", image = "jobimage")
    val template = Pod.Template.Spec.named("jobtemplatespecname").addContainer(container)
    val job = Job("jobname").withTemplate(template)

    val readJob = Json.fromJson[Job](Json.toJson(job)).get
    readJob mustEqual job
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
        |		}
        |	}
        |}
      """.stripMargin

    val job = Json.parse(jobJsonStr).as[Job]
    job.kind mustEqual "Job"
    job.name mustEqual "pi"
    val templateSpec: Template.Spec = job.spec.get.template.get
    templateSpec.metadata.name mustEqual "templatename"
    templateSpec.spec.get.restartPolicy mustEqual RestartPolicy.Never
    val container = templateSpec.spec.get.containers.head
    container.name mustEqual "containername"
    container.image mustEqual "perl"
  }
}
