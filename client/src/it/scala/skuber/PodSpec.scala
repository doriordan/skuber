package skuber

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import skuber.json.format._
import scala.concurrent.duration._
import java.util.UUID.randomUUID
import skuber.FutureUtil.FutureOps

class PodSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {

  val defaultLabels = Map("PodSpec" -> this.suiteName)
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  override def afterAll() = {
    val k8s = k8sInit
    val requirements = defaultLabels.toSeq.map { case (k, _) => LabelSelector.ExistsRequirement(k) }
    val labelSelector = LabelSelector(requirements: _*)
    val results = k8s.deleteAllSelected[PodList](labelSelector).withTimeout()
    results.futureValue

    results.onComplete { _ =>
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }
  }

  behavior of "Pod"

  it should "create a pod" in { k8s =>
    val podName1: String = randomUUID().toString
    val p = k8s.create(getNginxPod(podName1, "1.7.9")).valueT
    p.name shouldBe podName1
  }

  it should "get the newly created pod" in { k8s =>
    val podName2: String = randomUUID().toString
    k8s.create(getNginxPod(podName2, "1.7.9")).valueT
    eventually(timeout(30.seconds), interval(3.seconds)) {
      val p = k8s.get[Pod](podName2).valueT
      p.name shouldBe podName2
    }
  }

  it should "check for newly created pod and container to be ready" in { k8s =>
    val podName3: String = randomUUID().toString
    k8s.create(getNginxPod(podName3, "1.7.9")).valueT
    eventually(timeout(20.seconds), interval(3.seconds)) {
      val podRetrieved = k8s.get[Pod](podName3).valueT
      val podStatus = podRetrieved.status.get
      val nginxContainerStatus = podStatus.containerStatuses(0)
      podStatus.phase should contain(Pod.Phase.Running)
      nginxContainerStatus.name should be(podName3)
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
    val podName4: String = randomUUID().toString
    k8s.create(getNginxPod(podName4, "1.7.9")).valueT
    k8s.delete[Pod](podName4).valueT

    whenReady(k8s.get[Namespace](podName4).withTimeout().failed) { result =>
      result shouldBe a[K8SException]
      result match {
        case ex: K8SException => ex.status.code shouldBe Some(404)
        case _ => assert(false)
      }
    }
  }

  it should "delete selected pods" in { k8s =>
    val podName5: String = randomUUID().toString + "-foo"
    val podName6: String = randomUUID().toString + "-bar"
    k8s.create(getNginxPod(podName5, "1.7.9", labels = Map("foo" -> "1"))).valueT
    k8s.create(getNginxPod(podName6, "1.7.9", labels = Map("bar" -> "2"))).valueT
    Thread.sleep(5000)
    k8s.deleteAllSelected[PodList](LabelSelector(LabelSelector.ExistsRequirement("foo"))).valueT

    eventually(timeout(20.seconds), interval(3.seconds)) {
      val retrievePods = k8s.list[PodList]().withTimeout()
      val podsRetrieved = retrievePods.futureValue
      val podNamesRetrieved = podsRetrieved.items.map(_.name)
      assert(!podNamesRetrieved.contains(podName5) && podNamesRetrieved.contains(podName6))
    }
  }

  def getNginxContainer(name: String, version: String): Container = Container(name = name, image = "nginx:" + version).exposePort(80)

  def getNginxPod(name: String, version: String, labels: Map[String, String] = Map()): Pod = {
    val nginxContainer = getNginxContainer(name, version)
    val nginxPodSpec = Pod.Spec(containers = List((nginxContainer)))
    val podMeta = ObjectMeta(name = name, labels = labels ++ defaultLabels)
    Pod(metadata = podMeta, spec = Some(nginxPodSpec))
  }
}
