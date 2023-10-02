package skuber

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Outcome
import org.scalatest.flatspec.{AnyFlatSpec, FixtureAnyFlatSpec}
import skuber.FutureUtil.FutureOps
import skuber.LabelSelector.IsEqualRequirement
import skuber.api.client.KubernetesClient
import skuber.apps.v1.Deployment
import skuber.json.format.namespaceFormat
import scala.concurrent.ExecutionContextExecutor

trait K8SFixture extends FixtureAnyFlatSpec {

  override type FixtureParam = K8SRequestContext

  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

  val config: Config = ConfigFactory.load()

  override def withFixture(test: OneArgTest): Outcome = {
    val k8s: KubernetesClient = k8sInit(config)
    try {
      test(k8s)
    } finally {
      k8s.close
    }
  }

  def createNamespace(name: String, k8s: FixtureParam): Namespace = k8s.create[Namespace](Namespace.forName(name)).valueT

  def deleteNamespace(name: String, k8s: FixtureParam): Unit = k8s.delete[Namespace](name).withTimeout().recover { case _ => () }

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)

  def getNginxDeployment(name: String, version: String = "1.7.9", labels: Map[String, String] = Map.empty): Deployment = {
    import LabelSelector.dsl._
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    val labelSelector = LabelSelector(IsEqualRequirement("app", "nginx"))

    Deployment(name)
      .copy(metadata = ObjectMeta(name = name, labels = labels))
      .withTemplate(nginxTemplate)
      .withLabelSelector(labelSelector)
  }


}
