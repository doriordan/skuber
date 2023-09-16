package skuber

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Success

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers

import skuber.pekkoclient.k8sInit

import skuber.api.patch._
import skuber.json.format._
import skuber.model.{Container, ObjectMeta, Pod}

class PatchSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll {
  val nginxPodName: String = java.util.UUID.randomUUID().toString

  override def beforeAll(): Unit = {
    super.beforeAll()

    val k8s = k8sInit
    Await.result(k8s.create(getNginxPod(nginxPodName, "1.7.9")), 3.second)
    // Let the pod running
    Thread.sleep(3000)
    k8s.close
  }

  override def afterAll(): Unit = {
    val k8s = k8sInit
    Await.result(k8s.delete[Pod](nginxPodName), 3.second)
    Thread.sleep(3000)
    k8s.close

    super.afterAll()
  }

  behavior of "Patch"

  it should "patch a pod with strategic merge patch by default" in { k8s =>
    val randomString = java.util.UUID.randomUUID().toString
    val patchData = MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None)
    k8s.patch[MetadataPatch, Pod](nginxPodName, patchData).map { _ =>
      eventually(timeout(10.seconds), interval(1.seconds)) {
        val retrievePod = k8s.get[Pod](nginxPodName)
        val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
        podRetrieved match {
          case Success(pod: Pod) =>
            assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
            assert(pod.metadata.annotations == Map())
          case _ => assert(false)
        }
      }
    }
  }

  it should "patch a pod with strategic merge patch" in { k8s =>
    val randomString = java.util.UUID.randomUUID().toString
    val patchData = new MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None, strategy = StrategicMergePatchStrategy)
    k8s.patch[MetadataPatch, Pod](nginxPodName, patchData).map { _ =>
      eventually(timeout(10.seconds), interval(1.seconds)) {
        val retrievePod = k8s.get[Pod](nginxPodName)
        val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
        podRetrieved match {
          case Success(pod: Pod) =>
            assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
            assert(pod.metadata.annotations == Map())
          case _ => assert(false)
        }
      }
    }
  }

  it should "patch a pod with json merge patch" in { k8s =>
    val randomString = java.util.UUID.randomUUID().toString
    val patchData = new MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None, strategy = JsonMergePatchStrategy)
    k8s.patch[MetadataPatch, Pod](nginxPodName, patchData).map { _ =>
      eventually(timeout(10.seconds), interval(1.seconds)) {
        val retrievePod = k8s.get[Pod](nginxPodName)
        val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
        podRetrieved match {
          case Success(pod: Pod) =>
            assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
            assert(pod.metadata.annotations == Map())
          case _ => assert(false)
        }
      }
    }
  }

  it should "patch a pod with json patch" in { k8s =>
    val randomString = java.util.UUID.randomUUID().toString
    val annotations = Map("skuber" -> "wow")
    val patchData = JsonPatch(List(
      JsonPatchOperation.Add("/metadata/labels/foo", randomString),
      JsonPatchOperation.Add("/metadata/annotations", randomString),
      JsonPatchOperation.Remove("/metadata/annotations"),
    ))
    k8s.patch[JsonPatch, Pod](nginxPodName, patchData).map { _ =>
      eventually(timeout(10.seconds), interval(1.seconds)) {
        val retrievePod = k8s.get[Pod](nginxPodName)
        val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
        podRetrieved match {
          case Success(pod: Pod) =>
            assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
            assert(pod.metadata.annotations == Map())
          case _ => assert(false)
        }
      }
    }
  }

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)

  def getNginxPod(name: String, version: String): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec = Pod.Spec(containers = List((nginxContainer)))
    Pod(metadata = ObjectMeta(nginxPodName, labels = Map("label" -> "1"), annotations = Map("annotation" -> "1"))
      , spec = Some(nginxPodSpec))
  }
}
