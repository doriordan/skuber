package skuber

import java.util.UUID.randomUUID
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers}
import skuber.json.format.{namespaceFormat, podFormat}
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * @author David O'Riordan
 */
class NamespaceSpec extends K8SFixture with Eventually with Matchers with ScalaFutures with BeforeAndAfterAll {

  val nginxPodName1: String = randomUUID().toString

  val namespace1Name: String = randomUUID().toString
  val namespace2Name: String = randomUUID().toString
  val namespace3Name: String = randomUUID().toString
  val namespace4Name: String = randomUUID().toString
  val namespace5Name: String = randomUUID().toString

  def getPod(namespace: String): Pod = getNginxPod(namespace, nginxPodName1)

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(
      List(namespace1Name, namespace2Name, namespace2Name, namespace4Name, namespace5Name).map { name =>
        k8s.delete[Namespace](name).recover { case _ => () }
      })

    results.futureValue

    results.onComplete { _ =>
      k8s.close
    }
  }

  behavior of "Namespace"

  it should "create namespace1" in { k8s =>
    val ns = k8s.create(Namespace(namespace1Name)).futureValue
    assert(ns.name == namespace1Name)
  }

  it should "create pod1 in namespace4" in { k8s =>
    val pod = getPod(namespace2Name)
    k8s.create(Namespace(namespace2Name)).futureValue

    val p = k8s.usingNamespace(namespace2Name).create(pod).futureValue
    p.name shouldBe pod.name
    p.namespace shouldBe namespace2Name

  }

  it should "not find a a non exist namespace" in { k8s =>
    val nonExistNamespace: String = randomUUID().toString
    whenReady(
      k8s.get[Namespace](nonExistNamespace).failed
    ) { result =>
      result shouldBe a[K8SException]
      result match {
        case ex: K8SException => ex.status.code shouldBe Some(404)
        case _ => assert(false)
      }
    }
  }

  it should "find the pod1 in namespace1" in { k8s =>
    val pod = getPod(namespace4Name)
    k8s.create(Namespace(namespace4Name)).futureValue
    k8s.usingNamespace(namespace4Name).create(pod).futureValue

    val actualPod = k8s.usingNamespace(namespace4Name).get[Pod](pod.name).futureValue

    actualPod.name shouldBe pod.name
  }

  it should "delete namespace" in { k8s =>
    k8s.create(Namespace(namespace5Name)).futureValue

    // Delete namespaces
    k8s.delete[Namespace](namespace5Name).futureValue

    eventually(timeout(20.seconds), interval(3.seconds)) {
      k8s.get[Namespace](namespace5Name)

      whenReady(
        k8s.get[Namespace](namespace5Name).failed
      ) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }
  }

  def getNginxContainer(version: String): Container =
    Container(name = "nginx", image = "nginx:" + version)
      .exposePort(port = 80)

  def getNginxPod(namespace: String, name: String, version: String = "1.7.8"): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec = Pod.Spec(containers = List(nginxContainer))
    val podMeta = ObjectMeta(namespace = namespace, name = name)
    Pod(metadata = podMeta, spec = Some(nginxPodSpec))
  }
}
