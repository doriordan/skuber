package skuber

import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import skuber.api.client.K8SException
import skuber.json.format.serviceFmt
import skuber.model.Service

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

/**
 * Shared integration tests for Service operations that work with both Akka and Pekko clients.
 * The concrete fixture (AkkaK8SFixture or PekkoK8SFixture) is mixed in via build configuration.
 */
abstract class ServiceSpec extends K8SFixture with Eventually with Matchers {
  val nginxServiceName: String = Random.alphanumeric.filter(_.isLetter).take(20).mkString.toLowerCase

  behavior of "Service"

  it should "create a service" in {
    withK8sClient { k8s =>
      k8s.create(getService(nginxServiceName)) map { p =>
        assert(p.name == nginxServiceName)
      }
    }
  }

  it should "get the newly created service" in {
    withK8sClient { k8s =>
      k8s.get[Service](nginxServiceName) map { d =>
        assert(d.name == nginxServiceName)
        // Default ServiceType is ClusterIP
        assert(d.spec.map(_._type) == Option(Service.Type.ClusterIP))
      }
    }
  }

  it should "delete a service" in {
    withK8sClient { k8s =>
      k8s.delete[Service](nginxServiceName).map { _ =>
        eventually(timeout(100.seconds), interval(3.seconds)) {
          val retrieveService = k8s.get[Service](nginxServiceName)
          val serviceRetrieved = Await.ready(retrieveService, 2.seconds).value.get
          serviceRetrieved match {
            case s: Success[_] => fail("Deleted service still exists")
            case Failure(ex) => ex match {
              case ex: K8SException if ex.status.code.contains(404) => succeed
              case _ => fail(s"Unexpected exception: ${ex.getMessage}")
            }
          }
        }
      }
    }
  }

  def getService(name: String): Service = {
    val spec: Service.Spec = Service.Spec(ports = List(Service.Port(port = 80)), selector = Map("app" -> "nginx"))
    Service(name, spec)
  }
}