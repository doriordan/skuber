package skuber.examples.scale

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import skuber._
import skuber.autoscaling.HorizontalPodAutoscaler
import skuber.json.apps.format._
import skuber.apps._

/**
 * @author David O'Riordan
 * 
 * Some simple examples of using the extensions API to scale up and down a StatefulSet
 * or Deployment resource.
  * Note: StatefulSet scaling via this method is only supported by Kubernetes v1.8 and later
 */
object ScaleExamples extends App {
 
  def scaleNginx = {

    val nginxContainer = Container("nginx",image="nginx").exposePort(80)
    val nginxBaseSpec = Pod.Template.Spec().addContainer(nginxContainer)

    val nginxDeploymentLabels=Map("scale-example-type" -> "deployment")
    val nginxDeploymentSel=LabelSelector(LabelSelector.InRequirement("scale-example-type",List("deployment")))
    val nginxDeploymentSpec=nginxBaseSpec.addLabels(nginxDeploymentLabels)
    val nginxDeployment=Deployment("nginx-scale-depl")
       .withReplicas(10)
       .withLabelSelector(nginxDeploymentSel)
       .withTemplate(nginxDeploymentSpec)

    val nginxStsLabels=Map("scale-example-type" -> "statefulset")
    val nginxStsSel=LabelSelector(LabelSelector.InRequirement("scale-example-type",List("statefulset")))
    val nginxStsSpec=nginxBaseSpec.addLabels(nginxDeploymentLabels)
    val nginxStatefulSet= StatefulSet("nginx-scale-sts")
      .withReplicas(10)
      .withServiceName("nginx-service")
      .withLabelSelector(nginxStsSel)
      .withTemplate(nginxStsSpec)


    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher

    val k8s = k8sInit

    // First scale up and down a deployment
    println("Creating nginx deployment")

    val createdDeplFut = k8s create nginxDeployment

    val deplFut = createdDeplFut recoverWith {
      case ex: K8SException if (ex.status.code.contains(409)) => {
        println("It seems the deployment already exists - retrieving latest version")
        k8s get[Deployment] nginxDeployment.name
      }
    }
    println("Directly deployment down to 1 replica")

    val scaledDeploymentFut = for {
      scaledDown <- k8s.scale[Deployment](nginxDeployment.name, 1)
      _ = println("Scale desired = " + scaledDown.spec.replicas + ", current = " + scaledDown.status.get.replicas)
      _ = println("Now directly scale up to 4 replicas")
      scaledUp <- k8s.scale[Deployment](nginxDeployment.name, 4)
      _ = println("Scale object returned: specified = " + scaledUp.spec.replicas + ", current = " + scaledUp.status.get.replicas)
    } yield scaledUp

    println("waiting one minute to allow scaling to progress before deleting deployment")
    Thread.sleep(60000)
    println("Deleting deployment")
    k8s.deleteWithOptions[Deployment](nginxDeployment.name, DeleteOptions(propagationPolicy=Some(DeletePropagation.Foreground)))

    println("Creating nginx stateful set")
    val createdStsFut = k8s create nginxStatefulSet
   
    val stsFut = createdStsFut recoverWith {
      case ex: K8SException if (ex.status.code.contains(409)) => {
        println("It seems the stateful set already exists - retrieving latest version")
        k8s get[StatefulSet] nginxStatefulSet.name
      }
    }
    println("Directly scaling stateful set down to 1 replica")

    val scaledStsFut = for {
        scaledDown <- k8s.scale[StatefulSet](nginxStatefulSet.name, 1)
        _ = println("Scale desired = " + scaledDown.spec.replicas + ", current = " + scaledDown.status.get.replicas)
        _ = println("Now directly scaling it up to 4 replicas")
        scaledUp   <- k8s.scale[StatefulSet](nginxStatefulSet.name, 4)
        _ = println("Scale object returned: specified = " + scaledUp.spec.replicas + ", current = " + scaledUp.status.get.replicas)   
    } yield scaledUp

    println("waiting one minute to allow scaling to progress before deleting StatefulSet")
    Thread.sleep(60000)
    println("Deleting StatefulSet")
    k8s.deleteWithOptions[StatefulSet](nginxStatefulSet.name, DeleteOptions(propagationPolicy=Some(DeletePropagation.Foreground)))

    // Recreate the deployment, but this time to be scaled by a HPAS
    println("Recreating deployment for use with HPAS")
    val createdDeplFut2 = k8s create nginxDeployment

    val deplFut2 = createdDeplFut2 recoverWith {
      case ex: K8SException if (ex.status.code.contains(409)) => {
        println("It seems the deployment already exists - retrieving latest version")
        k8s get[Deployment] nginxDeployment.name
      }
    }

    val autoScale = for {
      depl  <- deplFut2
      hpas <- {
        println("Now creating a HorizontalPodAutoscaler to automatically scale the replicas")
        val hpas = HorizontalPodAutoscaler.scale(depl).
            withMinReplicas(2).
            withMaxReplicas(8).
            withCPUTargetUtilization(80)
        k8s create[HorizontalPodAutoscaler] hpas recover {
          case ex: K8SException if (ex.status.code.contains(409)) => {
            println("It seems the auto scaler already exists, so we are done.")
            hpas
          }
        }
      }
      _ = println("Successfully created horizontal pod autoscaler")
    } yield hpas

    autoScale onFailure {
      case k8sex: K8SException =>
        print("K8S Error => status code: " + k8sex.status.code +
            ",\n details=" + k8sex.status.details +
            "\n, message= " + k8sex.status.message.getOrElse("<>"))
      case ex: Exception => ex.printStackTrace
    }
    autoScale onComplete { case _ =>
      k8s.close
      system.terminate().foreach { f =>
        System.exit(0)
      }
    }
  }
  scaleNginx
}