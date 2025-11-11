package skuber

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import skuber.api.client.{K8SException, KubernetesClient}
import skuber.json.format._
import skuber.model.{Container, LabelSelector, ObjectMeta, Pod, PodList}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Shared integration tests for Pod operations that work with both Akka and Pekko clients.
 * The concrete fixture (AkkaK8SFixture or PekkoK8SFixture) is mixed in via build configuration.
 */
abstract class PodSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll {
  val nginxPodName: String = java.util.UUID.randomUUID().toString
  val defaultLabels = Map("app" -> this.suiteName)

  def createCleanupClient(): KubernetesClient[_, _, _] = createK8sClient(config)
  def closeCleanupClient(client: KubernetesClient[_, _, _]): Unit = {
    client.close()
  }

  override def afterAll(): Unit = {
    val k8s = createK8sClient(config)
    try {
      val requirements = defaultLabels.toSeq.map { case (k, v) => LabelSelector.IsEqualRequirement(k, v) }
      val labelSelector = LabelSelector(requirements: _*)
      Await.result(k8s.deleteAllSelected[PodList](labelSelector), 5.seconds)
    } finally {
      k8s.close()
    }
  }

  behavior of "Pod"

  it should "create a pod" in {
    withK8sClient { k8s =>
      k8s.create(getNginxPod(name = nginxPodName)) map { p =>
        assert(p.name == nginxPodName)
      }
    }
  }

  it should "get the newly created pod" in {
    withK8sClient { k8s =>
      k8s.get[Pod](nginxPodName) map { p =>
        assert(p.name == nginxPodName)
      }
    }
  }

  it should "check for newly created pod and container to be ready" in {
    withK8sClient { k8s =>
      eventually(timeout(100.seconds), interval(3.seconds)) {
        val retrievePod = k8s.get[Pod](nginxPodName)
        val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
        val podStatus = podRetrieved.get.status.get
        val nginxContainerStatus = podStatus.containerStatuses(0)
        podStatus.phase should contain(Pod.Phase.Running)
        nginxContainerStatus.name should be(defaultNginxContainerName)
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
        assert(!isUnschedulable)
        assert(isScheduled)
        assert(isInitialised)
        assert(isReady)
      }
    }
  }

  it should "delete a pod" in {
    withK8sClient { k8s =>
      k8s.delete[Pod](nginxPodName).map { _ =>
        eventually(timeout(100.seconds), interval(3.seconds)) {
          val retrievePod = k8s.get[Pod](nginxPodName)
          val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
          podRetrieved match {
            case s: Success[_] => fail("Deleted pod still exists")
            case Failure(ex) => ex match {
              case ex: K8SException if ex.status.code.contains(404) => succeed
              case _ => fail(s"Unexpected exception: ${ex.getMessage}")
            }
          }
        }
      }
    }
  }

  it should "delete selected pods" in {
    withK8sClient { k8s =>
      for {
        _ <- k8s.create(getNginxPodWithLabels(name = nginxPodName + "-foo", labels = Map("foo" -> "1") ++ defaultLabels))
        _ <- k8s.create(getNginxPodWithLabels(name = nginxPodName + "-bar", labels = Map("bar" -> "2") ++ defaultLabels))
        _ <- k8s.deleteAllSelected[PodList](LabelSelector(LabelSelector.ExistsRequirement("foo")))
      } yield eventually(timeout(100.seconds), interval(3.seconds)) {
        val retrievePods = k8s.list[PodList]()
        val podsRetrieved = Await.result(retrievePods, 2.seconds)
        val podNamesRetrieved = podsRetrieved.items.map(_.name)
        assert(!podNamesRetrieved.contains(nginxPodName + "-foo") && podNamesRetrieved.contains(nginxPodName + "-bar"))
      }
    }
  }
}