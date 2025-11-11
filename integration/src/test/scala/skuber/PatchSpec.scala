package skuber

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import skuber.api.patch._
import skuber.json.format._
import skuber.model.{Container, ObjectMeta, Pod}

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Shared integration tests for Patch operations that work with both Akka and Pekko clients.
 */
abstract class PatchSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll {
  val nginxPodName: String = java.util.UUID.randomUUID().toString

  override def beforeAll(): Unit = {
    super.beforeAll()

    withK8sClient { k8s =>
      Await.result(k8s.create(getNginxPodToPatch()), 3.second)
      // Let the pod startup
      Thread.sleep(3000)
      succeed
    }
  }

  override def afterAll(): Unit = {
    withK8sClient { k8s =>
      Await.result(k8s.delete[Pod](nginxPodName), 3.second)
      // allow time for termination
      Thread.sleep(3000)
      succeed
    }

    super.afterAll()
  }

  behavior of "Patch"

  it should "patch a pod with strategic merge patch by default" in {
    val randomString = java.util.UUID.randomUUID().toString
    val patchData = MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None)
    withK8sClient { k8s =>
      k8s.patch[MetadataPatch, Pod](nginxPodName, patchData).map { _ =>
        eventually(timeout(10.seconds), interval(1.seconds)) {
          val retrievePod = k8s.get[Pod](nginxPodName)
          val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
          podRetrieved match {
            case Success(pod: Pod) =>
              assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
              assert(pod.metadata.annotations == Map())
            case Failure(ex) => fail(s"Unable to retrieve patched pod: ${ex.getMessage}")
          }
        }
      }
    }
  }

  it should "patch a pod with strategic merge patch" in {
    val randomString = java.util.UUID.randomUUID().toString
    val patchData = new MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None, strategy = StrategicMergePatchStrategy)
    withK8sClient { k8s =>
      k8s.patch[MetadataPatch, Pod](nginxPodName, patchData).map { _ =>
        eventually(timeout(10.seconds), interval(1.seconds)) {
          val retrievePod = k8s.get[Pod](nginxPodName)
          val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
          podRetrieved match {
            case Success(pod: Pod) =>
              assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
              assert(pod.metadata.annotations == Map())
            case other => fail(s"Failed to retrieve patched pod: $other")
          }
        }
      }
    }
  }

  it should "patch a pod with json merge patch" in {
    val randomString = java.util.UUID.randomUUID().toString
    val patchData = new MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None, strategy = JsonMergePatchStrategy)
    withK8sClient { k8s =>
      k8s.patch[MetadataPatch, Pod](nginxPodName, patchData).map { _ =>
        eventually(timeout(10.seconds), interval(1.seconds)) {
          val retrievePod = k8s.get[Pod](nginxPodName)
          val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
          podRetrieved match {
            case Success(pod: Pod) =>
              assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
              assert(pod.metadata.annotations == Map())
            case other => fail(s"Failed to retrieve patched pod: $other")
          }
        }
      }
    }
  }

  it should "patch a pod with json patch" in {
    val randomString = java.util.UUID.randomUUID().toString
    val patchData = JsonPatch(List(
      JsonPatchOperation.Add("/metadata/labels/foo", randomString),
      JsonPatchOperation.Add("/metadata/annotations", randomString),
      JsonPatchOperation.Remove("/metadata/annotations"),
    ))
    withK8sClient { k8s =>
      k8s.patch[JsonPatch, Pod](nginxPodName, patchData).map { _ =>
        eventually(timeout(10.seconds), interval(1.seconds)) {
          val retrievePod = k8s.get[Pod](nginxPodName)
          val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
          podRetrieved match {
            case Success(pod: Pod) =>
              assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
              assert(pod.metadata.annotations == Map())
            case other => fail(s"Failed to retrieve patched pod: $other")
          }
        }
      }
    }
  }

  def getNginxPodToPatch(version: String = defaultNginxVersion): Pod = {
    val nginxContainer = getNginxContainer(version = version)
    val nginxPodSpec = Pod.Spec(containers = List((nginxContainer)))
    Pod(metadata = ObjectMeta(name = nginxPodName, labels = Map("label" -> "1"), annotations = Map("annotation" -> "1"))
      , spec = Some(nginxPodSpec))
  }
}