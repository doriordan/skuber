package skuber.mock

import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import skuber.{Container, Pod}
import skuber.json.format._

class PodSpec extends MockK8SFixture with Eventually with Matchers {
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

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)

  def getNginxPod(name: String, version: String): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec = Pod.Spec(containers=List(nginxContainer))
    Pod.named(nginxPodName).copy(spec=Some(nginxPodSpec))
  }
}
