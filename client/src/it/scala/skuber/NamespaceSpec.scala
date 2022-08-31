package skuber

import java.util.UUID.randomUUID
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import skuber.FutureUtil.FutureOps
import skuber.json.format.{namespaceFormat, podFormat}
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * @author David O'Riordan
 */
class NamespaceSpec extends K8SFixture with Eventually with Matchers with ScalaFutures with BeforeAndAfterAll {

  def nginxPodName1: String = randomUUID().toString

  val namespace1: String = randomUUID().toString
  val namespace2: String = randomUUID().toString
  val namespace3: String = randomUUID().toString
  val namespace4: String = randomUUID().toString

  def getPod(namespace: String): Pod = getNginxPod(namespace, nginxPodName1)

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(namespace1, namespace2, namespace3, namespace4).map { name =>
        k8s.delete[Namespace](name).withTimeout().recover { case _ => () }
      }).withTimeout()

    results.futureValue

    results.onComplete { _ =>
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }
  }

  behavior of "Namespace"

  it should "create namespace1" in { k8s =>
    println("START: create namespace1")
    val ns = k8s.create(Namespace(namespace1)).valueT
    println("FINISH: create namespace1")
    assert(ns.name == namespace1)
  }

  it should "create pod1 in namespace2" in { k8s =>
    println("START: create pod1 in namespace2")
    val pod = getPod(namespace2)
    k8s.create(Namespace(namespace2)).valueT
    eventually(timeout(30.seconds), interval(3.seconds)) {
      val p = k8s.usingNamespace(namespace2).create(pod).valueT
      println("FINISH: create pod1 in namespace2")
      p.name shouldBe pod.name
      p.namespace shouldBe namespace2
    }
  }

  it should "not find a a non exist namespace" in { k8s =>
    println("START: not find a a non exist namespace")
    val nonExistNamespace: String = randomUUID().toString
    whenReady(k8s.get[Namespace](nonExistNamespace).withTimeout().failed) { result =>
      println("FINISH: not find a a non exist namespace")
      result shouldBe a[K8SException]
      result match {
        case ex: K8SException => ex.status.code shouldBe Some(404)
        case _ => assert(false)
      }
    }
  }

  it should "find the pod1 in namespace3" in { k8s =>
    println("START: find the pod1 in namespace3")
    k8s.create(Namespace(namespace3)).valueT
    eventually(timeout(30.seconds), interval(3.seconds)) {
      val pod = getPod(namespace3)
      k8s.usingNamespace(namespace3).create(pod).valueT
      Thread.sleep(5000)

      val actualPod = k8s.usingNamespace(namespace3).get[Pod](pod.name).valueT
      println("FINISH: find the pod1 in namespace3")
      actualPod.name shouldBe pod.name
    }
  }

  it should "delete namespace4" in { k8s =>
    println("START: delete namespace4")
    k8s.create(Namespace(namespace4)).valueT
    Thread.sleep(5000)
    // Delete namespaces
    k8s.delete[Namespace](namespace4).valueT

    eventually(timeout(20.seconds), interval(3.seconds)) {
      whenReady(k8s.get[Namespace](namespace4).withTimeout().failed) { result =>
        println("FINISH: delete namespace4")
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }
  }

  def getNginxPod(namespace: String, name: String, version: String = "1.7.8"): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec = Pod.Spec(containers = List(nginxContainer))
    val podMeta = ObjectMeta(namespace = namespace, name = name)
    Pod(metadata = podMeta, spec = Some(nginxPodSpec))
  }
}
