package skuber

import java.util.UUID.randomUUID
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import skuber.FutureUtil.FutureOps
import skuber.json.format.{namespaceFormat, serviceFmt, serviceListFmt}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random
import LabelSelector.dsl._
import skuber.LabelSelector.IsEqualRequirement

class ServiceSpec extends K8SFixture with Eventually with BeforeAndAfterAll with ScalaFutures with Matchers with TestRetry {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  val defaultLabels = Map("ServiceSpec" -> this.suiteName)
  val serviceName1: String = generateServiceName
  val serviceName2: String = generateServiceName
  val serviceName3: String = generateServiceName
  val serviceName41: String = generateServiceName
  val serviceName42: String = generateServiceName
  val serviceName51: String = generateServiceName
  val serviceName52: String = generateServiceName

  val namespace4: String = randomUUID().toString
  val namespace5: String = randomUUID().toString

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(serviceName1, serviceName2, serviceName3, serviceName41, serviceName42).map { name =>
        k8s.delete[Service](name).withTimeout().recover { case _ => () }
      }).withTimeout()

    val results2 = Future.sequence(List(namespace4, namespace5).map { name =>
        k8s.delete[Namespace](name).withTimeout().recover { case _ => () }
      }).withTimeout()

    results.futureValue
    results2.futureValue

    results.onComplete { _ =>
      results2.onComplete { _ =>
        k8s.close
        system.terminate().recover { case _ => () }.valueT
      }
    }
  }

  def generateServiceName: String = Random.alphanumeric.filter(_.isLetter).take(20).mkString.toLowerCase


  behavior of "Service"

  it should "create a service" in { k8s =>
    val p = k8s.create(getService(serviceName1)).valueT
    assert(p.name == serviceName1)

  }

  it should "get the newly created service" in { k8s =>
    k8s.create(getService(serviceName2)).valueT
    val d = k8s.get[Service](serviceName2).valueT
    assert(d.name == serviceName2)
    // Default ServiceType is ClusterIP
    assert(d.spec.map(_._type) == Option(Service.Type.ClusterIP))

  }

  it should "delete a service" in { k8s =>
    k8s.create(getService(serviceName3)).valueT
    k8s.delete[Service](serviceName3).valueT
    eventually(timeout(20.seconds), interval(3.seconds)) {
      whenReady(k8s.get[Service](serviceName3).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }

  }


  it should "listSelected services in specific namespace" in { k8s =>
    createNamespace(namespace4, k8s)
    val labels = Map("listSelected" -> "true")
    val labelSelector = LabelSelector(IsEqualRequirement("listSelected", "true"))
    k8s.create(getService(serviceName41, labels), Some(namespace4)).valueT
    k8s.create(getService(serviceName42, labels), Some(namespace4)).valueT

    val expectedServices = List(serviceName41, serviceName42)
    val actualServices =
      k8s.listSelected[ServiceList](labelSelector, Some(namespace4)).valueT.map(_.name)

    actualServices should contain theSameElementsAs expectedServices

  }

  it should "listWithOptions services in specific namespace" in { k8s =>
    createNamespace(namespace5, k8s)
    val labels = Map("listWithOptions" -> "true")
    val labelSelector = LabelSelector(IsEqualRequirement("listWithOptions", "true"))
    val listOptions = ListOptions(Some(labelSelector))
    k8s.create(getService(serviceName51, labels), Some(namespace5)).valueT
    k8s.create(getService(serviceName52, labels), Some(namespace5)).valueT

    val expectedServices = List(serviceName51, serviceName52)
    val actualServices =
      k8s.listWithOptions[ServiceList](listOptions, Some(namespace5)).valueT.map(_.name)

    actualServices should contain theSameElementsAs expectedServices

  }

  def getService(name: String, labels: Map[String, String] = Map.empty): Service = {
    val spec: Service.Spec = Service.Spec(ports = List(Service.Port(port = 80)), selector = Map("app" -> "nginx"))
    val serviceMeta = ObjectMeta(name = name, labels = defaultLabels ++ labels)
    Service(name, spec).copy(metadata = serviceMeta)
  }
}
