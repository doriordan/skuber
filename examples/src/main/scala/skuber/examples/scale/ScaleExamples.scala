package skuber.examples.scale

import akka.actor.ActorSystem

import skuber._
import skuber.autoscaling.HorizontalPodAutoscaler
import skuber.json.format._
import skuber.json.apps.format._
import skuber.apps._

import scala.concurrent.Await
import scala.concurrent.duration.Duration.Inf

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
    val nginxDeploymentSel=LabelSelector(LabelSelector.IsEqualRequirement("scale-example-type","deployment"))
    val nginxDeploymentSpec=nginxBaseSpec.addLabels(nginxDeploymentLabels)
    val nginxDeployment=Deployment("nginx-scale-depl")
       .withReplicas(10)
       .withLabelSelector(nginxDeploymentSel)
       .withTemplate(nginxDeploymentSpec)

    val nginxStsLabels=Map("scale-example-type" -> "statefulset")
    val nginxStsSel=LabelSelector(LabelSelector.IsEqualRequirement("scale-example-type","statefulset"))
    val nginxStsSpec=nginxBaseSpec.addLabels(nginxStsLabels)
    val nginxStatefulSet= StatefulSet("nginx-scale-sts")
      .withReplicas(10)
      .withServiceName("nginx-scale-sts")
      .withLabelSelector(nginxStsSel)
      .withTemplate(nginxStsSpec)

    // StatefulSet needs a headless service
    val nginxStsService: Service=Service(nginxStatefulSet.spec.get.serviceName.get, nginxStsLabels, 80).isHeadless

    implicit val system = ActorSystem()
    implicit val dispatcher = system.dispatcher

    val k8s = k8sInit

    // First scale up and down a deployment
    println("Creating nginx deployment")

    val createdDeplFut = k8s create nginxDeployment

    val deplFut = createdDeplFut recoverWith {
      case ex: K8SException if (ex.status.code.contains(409)) => {
        println("It seems the deployment already exists - retrieving latest version")
        k8s.get[Deployment](nginxDeployment.name)
      }
    }

    val scaledDeploymentFut = for {
      del <- deplFut // wait for deployment to be created before scaling
      _ = println("Directly deployment down to 1 replica")
      currentScale <- k8s.getScale[Deployment](nginxDeployment.name)
      downScale = currentScale.withSpecReplicas(1)
      scaledDown <- k8s.updateScale[Deployment](nginxDeployment.name, downScale)
      _ = println("Scale desired = " + scaledDown.spec.replicas + ", current = " + scaledDown.status.get.replicas)
      _ = println("Now directly scale up to 4 replicas")
      upScale = scaledDown.withSpecReplicas(4)
      scaledUp <- k8s.updateScale[Deployment](nginxDeployment.name, upScale)
      _ = println("Scale object returned: specified = " + scaledUp.spec.replicas + ", current = " + scaledUp.status.get.replicas)
    } yield scaledUp

    Await.ready(scaledDeploymentFut, Inf)

    println("waiting one minute to allow scaling to complete before deleting deployment")
    Thread.sleep(60000)
    println("will now delete deployment")
    val deploymentDeletedFut = k8s.deleteWithOptions[Deployment](nginxDeployment.name, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))

    // wait for deployment deletion to be acknowledged before moving on to stateful set
    Await.ready(deploymentDeletedFut, Inf)

    println("Creating nginx stateful set")
    val createdStsFut = for {
      svc <- k8s create nginxStsService
      sts <- k8s create nginxStatefulSet
    } yield sts
   
    val stsFut = createdStsFut recoverWith {
      case ex: K8SException if (ex.status.code.contains(409)) => {
        println("It seems the stateful set or service already exists - retrieving latest version")
        k8s.get[StatefulSet](nginxStatefulSet.name)
      }
    }

    // Wait for stateful set creation before proceeding
    Await.ready(stsFut, Inf)
    println("waiting three minutes to allow Stateful Set creation to complete before scaling it")
    Thread.sleep(300000)

    println("Directly scaling stateful set down to 1 replica")
    val scaledStsFut = for {
      currentScale <- k8s.getScale[StatefulSet](nginxStatefulSet.name)
      downScale = currentScale.withSpecReplicas(1)
      scaledDown <- k8s.updateScale[StatefulSet](nginxStatefulSet.name, downScale)
      _ = println("Scale desired = " + scaledDown.spec.replicas + ", current = " + scaledDown.status.get.replicas)
      _ = println("Now directly scaling it up to 4 replicas")
      scaledUp   <- k8s.updateScale[StatefulSet](nginxStatefulSet.name, scaledDown.withSpecReplicas(4))
      _ = println("Scale desired = " + scaledUp.spec.replicas + ", current = " + scaledUp.status.get.replicas)
    } yield scaledUp

    Await.ready(scaledStsFut, Inf)
    // scaling down can take a while with stateful sets as pods are terminated one at a time, so give it plenty of time
    println("waiting 10 minutes to allow scaling down to complete before deleting the StatefulSet")
    Thread.sleep(600000)
    println("will now delete StatefulSet and its service")
    val stsDelFut = for {
      sts <- k8s.deleteWithOptions[StatefulSet](nginxStatefulSet.name, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))
      done <- k8s.delete[Service](nginxStsService.name)
    } yield done

    // wait for stateful set deletion to be acknowledged before moving on to deployment with HPAS
    Await.ready(stsDelFut, Inf)

    // Recreate the deployment, but this time to be scaled by a HPAS
    println("Recreating deployment for use with HPAS")

    val autoscaleDone = for {
      depl  <- k8s create nginxDeployment
      _ =  println("Now creating a HorizontalPodAutoscaler to automatically scale the replicas")
      _ =  println("This should cause the replica count to fall to 8 or below")
      hpas = HorizontalPodAutoscaler.scale(depl).
               withMinReplicas(2).
               withMaxReplicas(8).
               withCPUTargetUtilization(80)
      hpa  <- k8s.create[HorizontalPodAutoscaler](hpas)
      _ = {
            println("Successfully created horizontal pod autoscaler")
            println("waiting one minute to allow scaling to progress before cleaning up")
            // Note: should see replica count for deployment fall to 8 or below
            Thread.sleep(60000)
            println("will now delete hpa and deployment")
          }
      _ <-  k8s.delete[HorizontalPodAutoscaler](hpas.name)
      done <- k8s.deleteWithOptions[Deployment](depl.name, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))
    } yield done

    Await.ready(autoscaleDone, Inf)
    println("Finishing up")
    k8s.close
    system.terminate()
  }
  scaleNginx
}