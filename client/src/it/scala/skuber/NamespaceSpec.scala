package skuber

import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually

import json.format.{namespaceFormat,podFormat}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
  * @author David O'Riordan
  */
class NamespaceSpec extends K8SFixture with Eventually with Matchers {

  val nginxPodName: String = java.util.UUID.randomUUID().toString

  val namespace1Name="namespace1"

  behavior of "Namespace"

  it should "create a namespace" in { k8s =>
    k8s.create(Namespace.forName(namespace1Name)).map { ns =>
      assert(ns.name == namespace1Name)
    }
  }

  it should "create a pod in the newly created namespace" in { k8s =>
    k8s.usingNamespace(namespace1Name).create(getNginxPod(namespace1Name, nginxPodName, "1.7.9")) map { p =>
      assert(p.name == nginxPodName)
      assert(p.namespace == namespace1Name)
    }
  }

  it should "not find the newly created pod in the default namespace" in { k8s =>
    val retrievePod = k8s.get[Pod](nginxPodName)
    val podRetrieved=Await.ready(retrievePod, 2 seconds).value.get
    podRetrieved match {
      case s: Success[_] => assert(false)
      case Failure(ex) => ex match {
        case ex: K8SException if ex.status.code.contains(404) => assert(true)
        case _ => assert(false)
      }
    }
  }


  it should "find the newly created pod in the newly created namespace" in { k8s =>
    k8s.usingNamespace(namespace1Name).get[Pod](nginxPodName) map { p =>
      assert(p.name == nginxPodName)
    }
  }

  it should "delete the namespace" in { k8s =>
    val deleteNs = k8s.delete[Namespace](namespace1Name)
    eventually(timeout(100 seconds), interval(3 seconds)) {
      val retrieveNs = k8s.get[Namespace](namespace1Name)
      val nsRetrieved = Await.ready(retrieveNs, 2 seconds).value.get
      nsRetrieved match {
        case s: Success[_] => assert(false)
        case Failure(ex) => ex match {
          case ex: K8SException if ex.status.code.contains(404) => assert(true)
          case _ => assert(false)
        }
      }
    }
  }

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)

  def getNginxPod(namespace: String, name: String, version: String): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec = Pod.Spec(containers=List((nginxContainer)))
    val podMeta=ObjectMeta(namespace=namespace, name = name)
    Pod(metadata=podMeta, spec=Some(nginxPodSpec))
  }
}
