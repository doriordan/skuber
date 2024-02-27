package skuber

import java.util.UUID.randomUUID
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import skuber.FutureUtil.FutureOps
import skuber.json.format.{svcAccountFmt, svcAcctListFmt}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random
import LabelSelector.dsl._
import skuber.LabelSelector.IsEqualRequirement

class ServiceAccountSpec extends K8SFixture with Eventually with BeforeAndAfterAll with ScalaFutures with Matchers with TestRetry {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  val defaultLabels = Map("ServiceAccountSpec" -> this.suiteName)
  val serviceAccountName1: String = generateServiceAccountName
  val serviceAccountName2: String = generateServiceAccountName
  val serviceAccountName3: String = generateServiceAccountName
  val serviceAccountName4: String = generateServiceAccountName
  val serviceAccountName41: String = generateServiceAccountName
  val serviceAccountName42: String = generateServiceAccountName
  val serviceAccountName51: String = generateServiceAccountName
  val serviceAccountName52: String = generateServiceAccountName

  val namespace4: String = randomUUID().toString
  val namespace5: String = randomUUID().toString

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(serviceAccountName1, serviceAccountName2, serviceAccountName3, serviceAccountName4, serviceAccountName41, serviceAccountName42, serviceAccountName51, serviceAccountName52).map { name =>
        k8s.delete[ServiceAccount](name).withTimeout().recover { case _ => () }
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

  def generateServiceAccountName: String = Random.alphanumeric.filter(_.isLetter).take(20).mkString.toLowerCase


  behavior of "ServiceAccount"

  it should "create a service account" in { k8s =>
    val p = k8s.create(getServiceAccount(serviceAccountName1)).valueT
    assert(p.name == serviceAccountName1)
  }

  it should "get the newly created service account" in { k8s =>
    k8s.create(getServiceAccount(serviceAccountName2)).valueT
    val d = k8s.get[ServiceAccount](serviceAccountName2).valueT
    assert(d.name == serviceAccountName2)
  }

  it should "delete a service account" in { k8s =>
    k8s.create(getServiceAccount(serviceAccountName3)).valueT
    k8s.delete[ServiceAccount](serviceAccountName3).valueT
    eventually(timeout(20.seconds), interval(3.seconds)) {
      whenReady(k8s.get[ServiceAccount](serviceAccountName3).withTimeout().failed) { result =>
        result shouldBe a[K8SException]
        result match {
          case ex: K8SException => ex.status.code shouldBe Some(404)
          case _ => assert(false)
        }
      }
    }

  }

  it should "respect the automountServiceAccountToken setting when creating and getting a service account" in { k8s =>
    k8s.create(getServiceAccount(serviceAccountName4, automountServiceAccountToken = Some(false))).valueT
    val d = k8s.get[ServiceAccount](serviceAccountName4).valueT
    assert(d.name == serviceAccountName4)
    assert(d.automountServiceAccountToken == Some(false))
  }

  it should "listSelected service accounts in specific namespace" in { k8s =>
    createNamespace(namespace4, k8s)
    val labels = Map("listSelected" -> "true")
    val labelSelector = LabelSelector(IsEqualRequirement("listSelected", "true"))
    k8s.create(getServiceAccount(serviceAccountName41, labels), Some(namespace4)).valueT
    k8s.create(getServiceAccount(serviceAccountName42, labels), Some(namespace4)).valueT

    val expectedServiceAccounts = List(serviceAccountName41, serviceAccountName42)
    val actualServiceAccounts =
      k8s.listSelected[ServiceAccountList](labelSelector, Some(namespace4)).valueT.map(_.name)

    actualServiceAccounts should contain theSameElementsAs expectedServiceAccounts

  }

  it should "listWithOptions service accounts in specific namespace" in { k8s =>
    createNamespace(namespace5, k8s)
    val labels = Map("listWithOptions" -> "true")
    val labelSelector = LabelSelector(IsEqualRequirement("listWithOptions", "true"))
    val listOptions = ListOptions(Some(labelSelector))
    k8s.create(getServiceAccount(serviceAccountName51, labels), Some(namespace5)).valueT
    k8s.create(getServiceAccount(serviceAccountName52, labels), Some(namespace5)).valueT

    val expectedServiceAccounts = List(serviceAccountName51, serviceAccountName52)
    val actualServiceAccounts =
      k8s.listWithOptions[ServiceAccountList](listOptions, Some(namespace5)).valueT.map(_.name)

    actualServiceAccounts should contain theSameElementsAs expectedServiceAccounts

  }

  def getServiceAccount(name: String, labels: Map[String, String] = Map.empty, automountServiceAccountToken: Option[Boolean] = None): ServiceAccount = {
    val serviceAccountMeta = ObjectMeta(name = name, labels = defaultLabels ++ labels)
    ServiceAccount(metadata = serviceAccountMeta, automountServiceAccountToken = automountServiceAccountToken)
  }
}
