package skuber

import org.scalatest.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import skuber.json.format._

import scala.concurrent.duration._

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
        println(s"Containe ${name} has status ${status}")
      }
      assert(nginxCont.isDefined)
    }
  }

  it should "delete a pod" in { k8s =>
    k8s.delete[Pod](nginxPodName).map { _ =>
      eventually(timeout(300 seconds), interval(3 seconds)) {
        val f: Future[Pod] = k8s.get[Pod](nginxPodName)
        ScalaFutures.whenReady(f.failed) { e =>
          e shouldBe a[K8SException]
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
