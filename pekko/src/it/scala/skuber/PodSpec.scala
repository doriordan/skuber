package skuber

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Failure, Success}

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers

import skuber.pekkoclient.k8sInit

import skuber.json.format._
import skuber.model.{Container, LabelSelector, ObjectMeta, Pod, PodList}
import skuber.api.client.K8SException

class PodSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll {
  val nginxPodName: String = java.util.UUID.randomUUID().toString
  val defaultLabels = Map("app" -> this.suiteName)

  override def afterAll() = {
    val k8s = k8sInit
    val requirements = defaultLabels.toSeq.map { case (k, v) => LabelSelector.IsEqualRequirement(k, v) }
    val labelSelector = LabelSelector(requirements: _*)
    Await.result(k8s.deleteAllSelected[PodList](labelSelector), 5.seconds)
  }

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
    eventually(timeout(100.seconds), interval(3.seconds)) {
      val retrievePod = k8s.get[Pod](nginxPodName)
      val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
      val podStatus = podRetrieved.get.status.get
      val nginxContainerStatus = podStatus.containerStatuses(0)
      podStatus.phase should contain(Pod.Phase.Running)
      nginxContainerStatus.name should be(nginxPodName)
      nginxContainerStatus.state.get shouldBe a[Container.Running]
      val isUnschedulable = podStatus.conditions.exists { c =>
        c._type == "PodScheduled" && c.status == "False" && c.reason == Some("Unschedulable")
      }
      val isScheduled = podStatus.conditions.exists { c =>
        c._type == "PodScheduled" && c.status == "True"
      }
      val isInitialised = podStatus.conditions.exists { c =>
        c._type == "Initialized" && c.status == "True"
      }
      val isReady = podStatus.conditions.exists { c =>
        c._type == "Ready" && c.status == "True"
      }
      assert(isScheduled)
      assert(isInitialised)
      assert(isReady)
    }
  }

  it should "delete a pod" in { k8s =>
    k8s.delete[Pod](nginxPodName).map { _ =>
      eventually(timeout(100.seconds), interval(3.seconds)) {
        val retrievePod = k8s.get[Pod](nginxPodName)
        val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
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

  it should "delete selected pods" in { k8s =>
    for {
      _ <- k8s.create(getNginxPod(nginxPodName + "-foo", "1.7.9", labels = Map("foo" -> "1")))
      _ <- k8s.create(getNginxPod(nginxPodName + "-bar", "1.7.9", labels = Map("bar" -> "2")))
      _ <- k8s.deleteAllSelected[PodList](LabelSelector(LabelSelector.ExistsRequirement("foo")))
    } yield eventually(timeout(100.seconds), interval(3.seconds)) {
      val retrievePods = k8s.list[PodList]()
      val podsRetrieved = Await.result(retrievePods, 2.seconds)
      val podNamesRetrieved = podsRetrieved.items.map(_.name)
      assert(!podNamesRetrieved.contains(nginxPodName + "-foo") && podNamesRetrieved.contains(nginxPodName + "-bar"))
    }
  }

  def getNginxContainer(name: String, version: String): Container = Container(name = name, image = "nginx:" + version).exposePort(80)

  def getNginxPod(name: String, version: String, labels: Map[String, String] = Map()): Pod = {
    val nginxContainer = getNginxContainer(name, version)
    val nginxPodSpec = Pod.Spec(containers = List((nginxContainer)))
    val podMeta=ObjectMeta(name = name, labels = labels ++ defaultLabels)
    model.Pod(metadata = podMeta, spec = Some(nginxPodSpec))
  }
}
