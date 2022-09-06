package skuber.apps.v1

import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.LabelSelector.{IsEqualRequirement, NotExistsRequirement, NotInRequirement}
import skuber.LabelSelector.dsl._
import skuber._
import scala.language.reflectiveCalls

class StatefulSetSpec extends Specification {
  "This is a unit specification for the skuber apps/v1 StatefulSet class. ".txt

  "A StatefulSet object can be constructed from a name and pod template spec" >> {
    val container=Container(name="example",image="example")
    val template=Pod.Template.Spec.named("example").addContainer(container)
    val stateSet=StatefulSet("example")
      .withReplicas(200)
      .withServiceName("nginx-service")
      .withTemplate(template)
      .withVolumeClaimTemplate(PersistentVolumeClaim("hello"))
    stateSet.spec.get.template mustEqual template
    stateSet.spec.get.serviceName mustEqual Some("nginx-service")
    stateSet.spec.get.replicas must beSome(200)
    stateSet.spec.get.volumeClaimTemplates.size mustEqual 1
    stateSet.name mustEqual "example"
    stateSet.status mustEqual None
  }

  "A StatefulSet object can be written to Json and then read back again successfully" >> {
    val container=Container(name="example",image="example")
    val template=Pod.Template.Spec.named("example").addContainer(container)
    val labelSelector = LabelSelector(NotExistsRequirement("live"), IsEqualRequirement("tier", "cache"), NotInRequirement("env", List("dev", "test")))
    val stateSet=StatefulSet("example")
      .withTemplate(template)
      .withLabelSelector(labelSelector)


    val readSSet = Json.fromJson[StatefulSet](Json.toJson(stateSet)).get
    readSSet mustEqual stateSet
  }

  "A StatefulSet object properly writes with zero replicas" >> {
    val sset=StatefulSet("example").withReplicas(0)

    val writeSSet = Json.toJson(sset)
    (writeSSet \ "spec" \ "replicas").asOpt[Int] must beSome(0)
  }

  "A StatefulSet object can be read directly from a JSON string" >> {
    import scala.io.Source
    val ssJsonSource=Source.fromURL(getClass.getResource("/exampleStatefulSet.json"))
    val ssetJsonStr = ssJsonSource.mkString
    val stateSet = Json.parse(ssetJsonStr).as[StatefulSet]

    stateSet.kind mustEqual "StatefulSet"
    stateSet.name mustEqual "nginx-stateset"
    stateSet.spec.get.replicas must beSome(7)
    stateSet.spec.get.updateStrategy.get.`type` mustEqual StatefulSet.UpdateStrategyType.RollingUpdate
    stateSet.spec.get.updateStrategy.get.rollingUpdate.get.partition mustEqual(5)
    stateSet.spec.get.volumeClaimTemplates.size mustEqual 1
    stateSet.spec.get.serviceName.get mustEqual "nginx-service"
    stateSet.spec.get.template.metadata.labels mustEqual Map("domain" -> "www.example.com","proxies" -> "microservices")
    val podSpec=stateSet.spec.get.template.spec.get
    podSpec.containers.length mustEqual 1
    val container=podSpec.containers(0)
    container.resources.get.requests.get("cpu").get mustEqual Resource.Quantity("500m")
    container.lifecycle.get.preStop.get mustEqual ExecAction(List("/bin/sh", "-c", "PID=$(pidof java) && kill $PID && while ps -p $PID > /dev/null; do sleep 1; done"))
    container.readinessProbe.get.action mustEqual ExecAction(List("/bin/sh", "-c", "./ready.sh"))
    container.readinessProbe.get.initialDelaySeconds mustEqual 15
    container.readinessProbe.get.timeoutSeconds mustEqual 5
    stateSet.spec.get.selector.get.requirements.size mustEqual 4
    stateSet.spec.get.selector.get.requirements.find(r => (r.key == "env")) mustEqual Some("env" isNotIn List("dev"))
    stateSet.spec.get.selector.get.requirements.find(r => (r.key == "domain")) mustEqual Some("domain" is "www.example.com")

    // write and read back in again, should be unchanged
    val json = Json.toJson(stateSet)
    val readSS = Json.fromJson[StatefulSet](json).get
    readSS mustEqual stateSet
  }
}
