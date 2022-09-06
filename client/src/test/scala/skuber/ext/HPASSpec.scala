package skuber.autoscaling
import org.specs2.mutable.Specification

import scala.math.BigInt
import skuber.{Container, ObjectMeta, Pod, ReplicationController}
import play.api.libs.json._
import skuber.autoscaling.HorizontalPodAutoscaler.CrossVersionObjectReference

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
      hpas.spec.scaleTargetRef.kind mustEqual "ReplicationController"
      hpas.spec.maxReplicas must_== 10
      hpas.spec.minReplicas must beSome(1)
      hpas.spec.cpuUtilization mustEqual Some(CPUTargetUtilization(80))
    }
  }

  "A HPAS object properly writes with zero minReplicas" >> {
    val hpas=HorizontalPodAutoscaler(metadata = ObjectMeta("example"),
      spec = HorizontalPodAutoscaler.Spec(scaleTargetRef = CrossVersionObjectReference(name="example"))).withMaxReplicas(0).withMinReplicas(0)

    val writeHPAS = Json.toJson(hpas)
    (writeHPAS \ "spec" \ "minReplicas").asOpt[Int] must beSome(0)
    (writeHPAS \ "spec" \ "maxReplicas").asOpt[Int] must beSome(0)
  }
}