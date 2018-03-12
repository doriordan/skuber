package skuber

import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import skuber.json.format._

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Success,Failure}

import akka.event.Logging

import akka.stream.scaladsl.Sink

import scala.concurrent.{Future}

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

  it should "get the status of newly created pod and container" in { k8s =>
    k8s.get[Pod](nginxPodName).map { p =>
      val nginxCont = for {
        podStatus <- p.status
        nginxContainerStatus = podStatus.containerStatuses(0)
        nginxContName = nginxContainerStatus.name
        nginxContStatus <- nginxContainerStatus.state
      } yield (nginxContName, nginxContStatus)
      nginxCont.map { case (name, status) =>
        println(s"Container ${name} has status ${status}")
      }
      assert(nginxCont.isDefined)
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
