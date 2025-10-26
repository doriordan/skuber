import skuber.model.LabelSelector.dsl._
import skuber.model.apps.v1.Deployment
import skuber.model.{Container, ObjectMeta, Pod}

import scala.language.reflectiveCalls

/**
  * @author David O'Riordan
  */
package object skuber {

  val defaultNginxVersion = "1.29.2"
  val defaultNginxPodName = "nginx"
  val defaultNginxContainerName = "nginx"

  def getNginxContainer(version: String, containerName: String = defaultNginxContainerName): Container = Container(name =  containerName, image = "nginx:" + version).exposePort(80)

  def getNginxPod(name: String = defaultNginxPodName, version: String = defaultNginxVersion): Pod = {
    val nginxContainer = getNginxContainer(version = version)
    val nginxPodSpec = Pod.Spec(containers = List((nginxContainer)))
    Pod(metadata = ObjectMeta(name, labels = Map("label" -> "1"), annotations = Map("annotation" -> "1"))
      , spec = Some(nginxPodSpec))
  }

  def getNginxPodWithNamespace(namespace: String, name: String = defaultNginxPodName, version: String = defaultNginxVersion): Pod = {
    val nginxContainer = getNginxContainer(version)
    val nginxPodSpec = Pod.Spec(containers = List((nginxContainer)))
    Pod(metadata = ObjectMeta(namespace = namespace, name = name, labels = Map("label" -> "1"), annotations = Map("annotation" -> "1"))
      , spec = Some(nginxPodSpec))
  }


  def getNginxPodWithLabels(name: String, labels: Map[String, String] = Map(), version: String = defaultNginxVersion): Pod = {
    val nginxContainer = getNginxContainer(version = version)
    val nginxPodSpec = Pod.Spec(containers = List((nginxContainer)))
    val podMeta = ObjectMeta(name = name, labels = labels)
    model.Pod(metadata = podMeta, spec = Some(nginxPodSpec))
  }

  def getNginxDeployment(deploymentName: String, version: String = defaultNginxVersion): Deployment = {
    val nginxContainer = getNginxContainer(version)
    val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
    Deployment(deploymentName).withTemplate(nginxTemplate).withLabelSelector("app" is "nginx")
  }
}
