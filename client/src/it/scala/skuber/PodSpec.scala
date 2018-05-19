package skuber

import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import skuber.json.format._

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

import akka.event.Logging
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.Future

class PodSpec extends K8SFixture with Eventually with Matchers {
  val nginxPodName: String = java.util.UUID.randomUUID().toString

  behavior of "Pod"

  it should "create a pod" in { k8s =>
    k8s.create(getNginxPod(nginxPodName, "1.7.9")) map { p =>
      assert(p.name == nginxPodName)
    }
  }

  it should "get the newly created pod" in { k8s =>
    k8s.get[Pod](nginxPodName) map { p =>
      assert(p.name == nginxPodName)
    }
  }

  it should "check for newly created pod and container to be ready" in { k8s =>
    eventually(timeout(100 seconds), interval(3 seconds)) {
      val retrievePod=k8s.get[Pod](nginxPodName)
      val podRetrieved=Await.ready(retrievePod, 2 seconds).value.get
      val podStatus=podRetrieved.get.status.get
      val nginxContainerStatus = podStatus.containerStatuses(0)
      podStatus.phase should contain(Pod.Phase.Running)
      nginxContainerStatus.name should be("nginx")
      nginxContainerStatus.state.get shouldBe a[Container.Running]
      val isUnschedulable=podStatus.conditions.exists { c =>
        c._type=="PodScheduled" && c.status=="False" && c.reason==Some("Unschedulable")
      }
      val isScheduled=podStatus.conditions.exists { c =>
        c._type=="PodScheduled" && c.status=="True"
      }
      val isInitialised=podStatus.conditions.exists { c =>
        c._type=="Initialized" && c.status=="True"
      }
      val isReady=podStatus.conditions.exists { c =>
        c._type=="Ready" && c.status=="True"
      }
      assert(isScheduled)
      assert(isInitialised)
      assert(isReady)
    }
  }

  it should "delete a pod" in { k8s =>
    k8s.delete[Pod](nginxPodName).map { _ =>
      eventually(timeout(100 seconds), interval(3 seconds)) {
        val retrievePod = k8s.get[Pod](nginxPodName)
        val podRetrieved=Await.ready(retrievePod, 2 seconds).value.get
        podRetrieved match {
          case s: Success[_] => assert(false)
          case Failure(ex) => ex match {
              case ex: K8SException if ex.status.code.contains(404) => assert(true)
              case _ => assert(false)
            }
        }
      }
    }
  }

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)

  def getNginxPod(name: String, version: String): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec = Pod.Spec(containers=List((nginxContainer)))
    Pod.named(nginxPodName).copy(spec=Some(nginxPodSpec))
  }
}
