package skuber

import java.util.UUID.randomUUID
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import skuber.FutureUtil.FutureOps
import skuber.api.dynamic.client.impl.{DynamicKubernetesClientImpl, DynamicKubernetesObject}
import skuber.apps.v1.Deployment
import scala.concurrent.Future

class DynamicKubernetesClientImplTest extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {

  val deploymentName1: String = randomUUID().toString

  private val kubernetesDynamicClient = DynamicKubernetesClientImpl.build()

  override def afterAll(): Unit = {
    val k8s = k8sInit(config)

    val results = Future.sequence(List(deploymentName1).map { name =>
      k8s.delete[Deployment](name).withTimeout().recover { case _ => () }
    }).withTimeout()

    results.futureValue

  }

  behavior of "DynamicKubernetesClientImplTest"


  it should "create a deployment" in { k8s =>

    val createdDeployment = k8s.create(getNginxDeployment(deploymentName1, "1.7.9")).valueT
    assert(createdDeployment.name == deploymentName1)

    val getDeployment: DynamicKubernetesObject = kubernetesDynamicClient.get(
      deploymentName1,
      apiVersion = "apps/v1",
      resourcePlural = "deployments").valueT

    assert(getDeployment.metadata.map(_.name).contains(deploymentName1))
  }

}
