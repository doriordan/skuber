package skuber

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers}
import skuber.json.format.{serviceFmt, serviceListFmt}
import scala.concurrent.duration._
import scala.util.Random

class ServiceSpec extends K8SFixture with Eventually with BeforeAndAfterAll with ScalaFutures with Matchers {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  val defaultLabels = Map("ServiceSpec" -> this.suiteName)

  override def afterAll(): Unit = {
    val k8s = k8sInit
    val requirements = defaultLabels.toSeq.map { case (k, _) => LabelSelector.ExistsRequirement(k) }
    val labelSelector = LabelSelector(requirements: _*)
    val results = k8s.deleteAllSelected[ServiceList](labelSelector).recover { case _ => () }
    results.futureValue

    results.onComplete { _ =>
      k8s.close
    }
  }

  def nginxServiceName: String = Random.alphanumeric.filter(_.isLetter).take(20).mkString.toLowerCase


  behavior of "Service"

  it should "create a service" in { k8s =>
    val serviceName1: String = nginxServiceName
    val p = k8s.create(getService(serviceName1)).futureValue
    assert(p.name == serviceName1)

  }

  it should "get the newly created service" in { k8s =>
    val serviceName2: String = nginxServiceName
    k8s.create(getService(serviceName2)).futureValue
    val d = k8s.get[Service](serviceName2).futureValue
    assert(d.name == serviceName2)
    // Default ServiceType is ClusterIP
    assert(d.spec.map(_._type) == Option(Service.Type.ClusterIP))

  }

  it should "delete a service" in { k8s =>
    val serviceName3: String = nginxServiceName
    k8s.create(getService(serviceName3)).futureValue
    k8s.delete[Service](serviceName3).futureValue
    eventually(timeout(20.seconds), interval(3.seconds)) {
      whenReady(
        k8s.get[Service](serviceName3).failed
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
