package skuber

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers}
import skuber.FutureUtil.FutureOps
import skuber.json.format.serviceFmt
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class ServiceSpec extends K8SFixture with Eventually with BeforeAndAfterAll with ScalaFutures with Matchers {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  val defaultLabels = Map("ServiceSpec" -> this.suiteName)
  val serviceName1: String = generateServiceName
  val serviceName2: String = generateServiceName
  val serviceName3: String = generateServiceName

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(
      List(serviceName1, serviceName2, serviceName3).map { name =>
        k8s.delete[Service](name).withTimeout().recover { case _ => () }
      })

    results.futureValue

    results.onComplete { _ =>
      k8s.close
      system.terminate()
    }
  }

  def generateServiceName: String = Random.alphanumeric.filter(_.isLetter).take(20).mkString.toLowerCase


  behavior of "Service"

  it should "create a service" in { k8s =>
    val p = k8s.create(getService(serviceName1)).withTimeout().futureValue
    assert(p.name == serviceName1)

  }

  it should "get the newly created service" in { k8s =>
    k8s.create(getService(serviceName2)).withTimeout().futureValue
    val d = k8s.get[Service](serviceName2).withTimeout().futureValue
    assert(d.name == serviceName2)
    // Default ServiceType is ClusterIP
    assert(d.spec.map(_._type) == Option(Service.Type.ClusterIP))

  }

  it should "delete a service" in { k8s =>
    k8s.create(getService(serviceName3)).withTimeout().futureValue
    k8s.delete[Service](serviceName3).withTimeout().futureValue
    eventually(timeout(20.seconds), interval(3.seconds)) {
      whenReady(
        k8s.get[Service](serviceName3).withTimeout().failed
      ) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }

  }

  def getService(name: String): Service = {
    val spec: Service.Spec = Service.Spec(ports = List(Service.Port(port = 80)), selector = Map("app" -> "nginx"))
    val serviceMeta = ObjectMeta(name = name, labels = defaultLabels)
    Service(name, spec).copy(metadata = serviceMeta)
  }
}
