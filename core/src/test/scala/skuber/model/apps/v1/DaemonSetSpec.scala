package skuber.model.apps.v1

import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.model.LabelSelector.dsl._
import skuber.model._

import scala.language.{postfixOps, reflectiveCalls}

/**
  * Created by jordan on 1/25/17.
  */
class DaemonSetSpec extends Specification {

  "A DaemonSet Object can be written to Json and then read back again successfully" >> {
    val container=Container(name="example",image="example")
    val template=Pod.Template.Spec.named("example").addContainer(container)
    val daemonset=DaemonSet("example")
        .withTemplate(template)
      .withLabelSelector(LabelSelector("live" doesNotExist, "microservice", "tier" is "cache", "env" isNotIn List("dev", "test")))

    val readDaemon = Json.fromJson[DaemonSet](Json.toJson(daemonset)).get
    readDaemon must beEqualTo(daemonset)
  }
}
