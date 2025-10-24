package skuber

import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import skuber.api.client._
import skuber.json.format.{namespaceFormat, podFormat}
import skuber.model.{Container, Namespace, ObjectMeta, Pod}

import java.util.UUID.randomUUID
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Integration tests for Namespace operations.
 *
 * @author David O'Riordan
 */
abstract class NamespaceSpec extends K8SFixture[_, _, _] with Eventually with Matchers {

  val nginxPodName1: String = randomUUID().toString
  val nginxPodName2: String = randomUUID().toString

  val namespace1Name: String = "namespace1"
  val namespace2Name: String = "namespace2"
  val testNamespaces = List(namespace1Name, namespace2Name)

  val pod1: Pod = getNginxPod(namespace1Name, nginxPodName1)
  val pod2: Pod = getNginxPod(namespace2Name, nginxPodName2)

  behavior of "Namespace"

  it should "create namespace1" in { k8s =>
    k8s.create(Namespace(namespace1Name)).map { ns => assert(ns.name == namespace1Name) }
  }

  it should "create namespace2" in { k8s =>
    k8s.create(Namespace(namespace2Name)).map { ns => assert(ns.name == namespace2Name) }
  }

  it should "create pod1 in namespace1" in { k8s =>
    k8s.usingNamespace(namespace1Name).create(pod1)
        .map { p =>
          assert(p.name == nginxPodName1)
          assert(p.metadata.namespace == namespace1Name)
        }
  }

  it should "honor namespace precedence hierarchy: object > client" in { k8s =>
    k8s.usingNamespace(namespace1Name).create(pod2).map { p =>
      assert(p.name == nginxPodName2)
      assert(p.metadata.namespace == namespace2Name)
    }
  }

  it should "find the pod2 in namespace2" in { k8s =>
    k8s.usingNamespace(namespace2Name).get[Pod](nginxPodName2) map { p =>
      assert(p.name == nginxPodName2)
    }
  }

  it should "not find pod1 in the default namespace" in { k8s =>
    val retrievePod = k8s.get[Pod](nginxPodName1)
    val podRetrieved = Await.ready(retrievePod, 2.seconds).value.get
    podRetrieved match {
      case s: Success[_] => assert(false)
      case Failure(ex) => ex match {
        case ex: K8SException if ex.status.code.contains(404) => assert(true)
        case _ => assert(false)
      }
    }
  }

  it should "find the pod1 in namespace1" in { k8s =>
    k8s.usingNamespace(namespace1Name).get[Pod](nginxPodName1) map { p =>
      assert(p.name == nginxPodName1)
    }
  }

  it should "delete all test namespaces" in { k8s =>
    object NotYetDeleted extends RuntimeException

    System.out.println("STARTING...")
    val t = timeout(100.seconds)
    val i = interval(2.seconds)
    // Delete namespaces
    testNamespaces.foreach { ns =>
      Await.result(k8s.delete[Namespace](ns), 2.seconds)
      eventually(t, i) {
        val gone = k8s.get[Namespace](ns)
        val goneResult = Await.ready(gone, 2.seconds).value.get
        goneResult match {
          case Failure(ex) => ex match {
            case k: K8SException if k.status.code.contains(404) =>
              System.err.println("Got 404 for " + ns)
            case _ =>
              System.err.println("Got unexpected exception for" + ns + ": " + ex.toString)
              throw NotYetDeleted
          }
          case _ =>
            System.err.println("Got success (still exists) for" + ns)
            throw NotYetDeleted
        }
      }
    }
    assert(condition = true)
  }

  def getNginxContainer(version: String): Container =
    Container(name = "nginx", image = "nginx:" + version)
      .exposePort(port = 80)

  def getNginxPod(namespace: String, name: String, version: String = "1.7.8"): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec   = Pod.Spec(containers=List(nginxContainer))
    val podMeta        = ObjectMeta(namespace=namespace, name = name)
    model.Pod(metadata=podMeta, spec=Some(nginxPodSpec))
  }
}