package skuber

import java.util.UUID.randomUUID
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import skuber.FutureUtil.FutureOps
import skuber.api.patch._
import skuber.json.format._
import scala.concurrent.Future
import scala.concurrent.duration._


class PatchSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {

  val pod1: String = randomUUID().toString
  val pod2: String = randomUUID().toString
  val pod3: String = randomUUID().toString
  val pod4: String = randomUUID().toString
  val pod5: String = randomUUID().toString
  val namespace5: String = randomUUID().toString

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(pod1, pod2, pod3, pod4).map { name =>
        k8s.delete[Pod](name).withTimeout().recover { case _ => () }
      }).withTimeout()

    results.futureValue

    results.onComplete { _ =>
      deleteNamespace(namespace5, k8s)
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }
  }

  behavior of "Patch"

  it should "patch a pod with strategic merge patch by default" in { k8s =>
    k8s.create(getNginxPod(pod1, "1.7.9")).withTimeout().futureValue
    Thread.sleep(5000)
    val randomString = randomUUID().toString
    val patchData = MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None)
    k8s.patch[MetadataPatch, Pod](pod1, patchData).valueT
    eventually(timeout(30.seconds), interval(3.seconds)) {
      val pod = k8s.get[Pod](pod1).valueT
      assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
      assert(pod.metadata.annotations == Map())
    }

  }

  it should "patch a pod with strategic merge patch" in { k8s =>
    k8s.create(getNginxPod(pod2, "1.7.9")).valueT
    Thread.sleep(5000)
    val randomString = randomUUID().toString
    val patchData = MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None, strategy = StrategicMergePatchStrategy)
    k8s.patch[MetadataPatch, Pod](pod2, patchData).valueT

    eventually(timeout(30.seconds), interval(3.seconds)) {
      val pod = k8s.get[Pod](pod2).valueT

      assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
      assert(pod.metadata.annotations == Map())
    }
  }

  it should "patch a pod with json merge patch" in { k8s =>
    k8s.create(getNginxPod(pod3, "1.7.9")).valueT
    Thread.sleep(5000)
    val randomString = randomUUID().toString
    val patchData = MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None, strategy = JsonMergePatchStrategy)
    k8s.patch[MetadataPatch, Pod](pod3, patchData).valueT
    eventually(timeout(30.seconds), interval(3.seconds)) {
      val pod = k8s.get[Pod](pod3).valueT

      assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
      assert(pod.metadata.annotations == Map())
    }
  }


  it should "patch a pod with json patch" in { k8s =>
    k8s.create(getNginxPod(pod4, "1.7.9")).valueT
    Thread.sleep(5000)
    val randomString = randomUUID().toString

    val patchData = JsonPatch(List(
      JsonPatchOperation.Add("/metadata/labels/foo", randomString),
      JsonPatchOperation.Add("/metadata/annotations", randomString),
      JsonPatchOperation.Remove("/metadata/annotations"),
    ))
    k8s.patch[JsonPatch, Pod](pod4, patchData).valueT
    eventually(timeout(30.seconds), interval(3.seconds)) {
      val pod = k8s.get[Pod](pod4).valueT

      assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
      assert(pod.metadata.annotations == Map())
    }
  }

  it should "patch a pod with json patch - specific namespace" in { k8s =>
    createNamespace(namespace5, k8s)
    k8s.create(getNginxPod(pod5, "1.7.9"), Some(namespace5)).valueT
    Thread.sleep(5000)
    val randomString = randomUUID().toString

    val patchData = JsonPatch(List(
      JsonPatchOperation.Add("/metadata/labels/foo", randomString),
      JsonPatchOperation.Add("/metadata/annotations", randomString),
      JsonPatchOperation.Remove("/metadata/annotations"),
    ))

    k8s.patch[JsonPatch, Pod](pod5, patchData, namespace = Some(namespace5)).valueT

    eventually(timeout(30.seconds), interval(3.seconds)) {
      val pod = k8s.get[Pod](pod5, Some(namespace5)).valueT

      assert(pod.metadata.labels == Map("label" -> "1", "foo" -> randomString))
      assert(pod.metadata.annotations == Map())
    }
  }


  def getNginxPod(name: String, version: String): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec = Pod.Spec(containers = List(nginxContainer))
    Pod(metadata = ObjectMeta(name, labels = Map("label" -> "1"), annotations = Map("annotation" -> "1")), spec = Some(nginxPodSpec))
  }
}
