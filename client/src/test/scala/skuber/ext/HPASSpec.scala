package skuber.ext

import org.specs2.mutable.Specification // for unit-style testing

import scala.math.BigInt

import skuber.{ReplicationController,Pod,Container, ObjectMeta}

import skuber.json.ext.format._

import play.api.libs.json._

/**
 * @author David O'Riordan
 */
class HPASSpec extends Specification {
  "This is a unit specification for the skuber HorizontalPodAutoscaler class. ".txt
  
  "A HPAS object can be constructed from the object it scales\n" >> {
    "A HPAS can be constructed from a replication controller" >> {
      val container=Container(name="example",image="example")
      val rc=ReplicationController("example", container, Map("app" -> "example"))
      val hpas=HorizontalPodAutoscaler.scale(rc).
                    withMinReplicas(1).
                    withMaxReplicas(10).
                    withCPUTargetUtilization(80)
      hpas.name mustEqual "example"   
      hpas.spec.scaleRef.kind mustEqual "ReplicationController"
      hpas.spec.scaleRef.subresource mustEqual "scale"
      hpas.spec.maxReplicas mustEqual 10
      hpas.spec.minReplicas mustEqual 1
      hpas.spec.cpuUtilization mustEqual Some(CPUTargetUtilization(80))
    }
  }
}