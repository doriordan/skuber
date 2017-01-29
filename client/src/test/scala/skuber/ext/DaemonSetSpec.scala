package skuber.ext

import org.specs2.mutable.Specification // for unit-style testing

import scala.math.BigInt

import skuber._
import skuber.LabelSelector.dsl._

import skuber.json.ext.format._

import play.api.libs.json._

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

    println(Json.toJson(daemonset))

    val readDaemon = Json.fromJson[DaemonSet](Json.toJson(daemonset)).get
    readDaemon mustEqual daemonset
  }
}
