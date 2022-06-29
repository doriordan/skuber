package skuber

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{Outcome, fixture}
import skuber.FutureUtil.FutureOps
import skuber.apps.v1.Deployment
import skuber.json.format.namespaceFormat
import scala.concurrent.ExecutionContextExecutor

trait K8SFixture extends fixture.FlatSpec {

  override type FixtureParam = K8SRequestContext

  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

  val config: Config = ConfigFactory.load()

  override def withFixture(test: OneArgTest): Outcome = {
    val k8s = k8sInit(config)
    try {
      test(k8s)
    } finally {
      k8s.close
    }
  }

  def createNamespace(name: String, k8s: FixtureParam): Namespace = k8s.create[Namespace](Namespace.forName(name)).valueT

  def getNginxContainer(version: String): Container = Container(name = "nginx", image = "nginx:" + version).exposePort(80)

  def getNginxDeployment(name: String, version: String): Deployment = {
    import LabelSelector.dsl._
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(nginxTemplate).withLabelSelector("app" is "nginx")
  }


}
