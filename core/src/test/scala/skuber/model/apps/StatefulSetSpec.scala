package skuber.model.apps

import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.model.LabelSelector.dsl._
import skuber.model._
import skuber.model.{Pod, Resource}

import scala.language.{postfixOps, reflectiveCalls}

/**
  * Created by hollinwilkins on 4/5/17.
  */
class StatefulSetSpec extends Specification {
  "This is a unit specification for the skuber StatefulSet class. ".txt

  "A StatefulSet object can be constructed from a name and pod template spec" >> {
    val container=Container(name="example",image="example")
    val template=Pod.Template.Spec.named("example").addContainer(container)
    val stateSet=StatefulSet("example")
      .withReplicas(200)
      .withServiceName("nginx-service")
      .withTemplate(template)
      .withVolumeClaimTemplate(PersistentVolumeClaim("hello"))
    stateSet.spec.get.template must beEqualTo(template)
    stateSet.spec.get.serviceName must beEqualTo(Some("nginx-service"))
    stateSet.spec.get.replicas must beSome(200)
    stateSet.spec.get.volumeClaimTemplates.size must beEqualTo(1)
    stateSet.name must beEqualTo("example")
    stateSet.status must beEqualTo(None)
  }

  "A StatefulSet object can be written to Json and then read back again successfully" >> {
    val container=Container(name="example",image="example")
    val template=Pod.Template.Spec.named("example").addContainer(container)
    val stateSet=StatefulSet("example")
      .withTemplate(template)
      .withLabelSelector(LabelSelector("live" doesNotExist, "microservice", "tier" is "cache", "env" isNotIn List("dev", "test")))


    val readSSet = Json.fromJson[StatefulSet](Json.toJson(stateSet)).get
    readSSet must beEqualTo(stateSet)
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

    stateSet.kind must beEqualTo("StatefulSet")
    stateSet.name must beEqualTo("nginx-stateset")
    stateSet.spec.get.replicas must beSome(7)
    stateSet.spec.get.updateStrategy.get.`type` must beEqualTo(StatefulSet.UpdateStrategyType.RollingUpdate)
    stateSet.spec.get.updateStrategy.get.rollingUpdate.get.partition must beEqualTo(5)
    stateSet.spec.get.volumeClaimTemplates.size must beEqualTo(1)
    stateSet.spec.get.serviceName.get must beEqualTo("nginx-service")
    stateSet.spec.get.template.metadata.labels must beEqualTo(Map("domain" -> "www.example.com","proxies" -> "microservices"))
    val podSpec=stateSet.spec.get.template.spec.get
    podSpec.containers.length must beEqualTo(1)
    val container=podSpec.containers.head
    container.resources.get.requests("cpu") must beEqualTo(Resource.Quantity("500m"))
    container.lifecycle.get.preStop.get must beEqualTo(ExecAction(List("/bin/sh", "-c", "PID=$(pidof java) && kill $PID && while ps -p $PID > /dev/null; do sleep 1; done")))
    container.readinessProbe.get.action must beEqualTo(ExecAction(List("/bin/sh", "-c", "./ready.sh")))
    container.readinessProbe.get.initialDelaySeconds must beEqualTo(15)
    container.readinessProbe.get.timeoutSeconds must beEqualTo(5)
    stateSet.spec.get.selector.get.requirements.size must beEqualTo(4)
    stateSet.spec.get.selector.get.requirements.find(r => (r.key == "env")) must beEqualTo(Some("env" isNotIn List("dev")))
    stateSet.spec.get.selector.get.requirements.find(r => (r.key == "domain")) must beEqualTo(Some("domain" is "www.example.com"))

    // write and read back in again, should be unchanged
    val json = Json.toJson(stateSet)
    val readSS = Json.fromJson[StatefulSet](json).get
    readSS must beEqualTo(stateSet)
  }
}
