package skuber.examples.deployment

import skuber._
import skuber.apps.Deployment
import skuber.json.apps.format._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author David O'Riordan
 * 
 * This demonstrates use of Deployment resources to manage installing and upgrading an app/service
 * on Kubernetes.
 * 
 * The steps it executes are:
 * 
 * 1. Create a deployment of nginx version 1.7.9
 * 2. Waits a little to allow initial deployment to complete
 * 3. Update the deployment to nginx version 1.9.1
 * 
 * After step 3 you can call 'kubectl describe deployment nginx-deployment' repeatedly to watch progress of the rolling update -
 * new pods will be created as old pods are torn down.
 * The rolling-update (default) strategy is requested, so that nginx pods should remain available even while
 * the deployment update is being processed.
 */
object DeploymentExamples extends App {
 
  val k8s = k8sInit
  
  val deployment = deployNginx("1.7.9") 
  
  deployment onSuccess {
    case depl => 
       
      // Wait for initial deployment to complete before updating it.
      // NOTE: Kubernetes v1.1 Deployment status subresource does not seem to be reliably populated
      // after initial create, although it does get populated later on update.
      // Hence just wait fixed time rather than polling for status changes to detemrine completion.
      // Revisit for v1.2, where the Deployment status will hopefully be always present:
      // https://github.com/kubernetes/kubernetes/commit/8acf01d6205166700754d0d744d15b454bc0669f
      
      // Can adjust time up/down depending on your environment and patience levels...
      // Initial pull of nginx images is likely to be greatest delay
      val waitingTime = 60 // seconds
      
      println("Successfully created deployment of nginx 1.7.9...now waiting " + waitingTime + " seconds before updating it")
      
      val reportInterval = 10 // seconds
      for (i <- 1 to (waitingTime/reportInterval)) {
        Thread.sleep(1000 * reportInterval)
        println ("..." + (waitingTime  - (i*reportInterval)) + " seconds to go")
      } 
      
      println("Updating deployment to nginx 1.9.1")
      updateNginx("1.9.1") onComplete {
        case scala.util.Success(_) =>
          println("Update successfully requested - use'kubectl describe deployments' to monitor progress")
          System.exit(0)
        case scala.util.Failure(ex) =>
          ex.printStackTrace()
          System.exit(1)
      }   
  }
  
  deployment onFailure {
    case ex =>
      ex.printStackTrace()
      System.exit(1)
  }
  
  def deployNginx(version: String) : Future[Deployment] = {
    
    val nginxLabel = "app" -> "nginx"
    val nginxContainer = Container(name="nginx",image="nginx:" + version).exposePort(80)
    
    val nginxTemplate = Pod.Template.Spec
      .named("nginx")
      .addContainer(nginxContainer)
      .addLabel(nginxLabel)
        
    val desiredCount = 5  
    val nginxDeployment = Deployment("nginx-deployment")
      .withReplicas(desiredCount)
      .withTemplate(nginxTemplate)
    
    val k8s = k8sInit
  
    println("Creating nginx deployment")
    val createdDeplFut = k8s create nginxDeployment
   
    createdDeplFut recoverWith {
      case ex: K8SException if (ex.status.code.contains(409)) => {
        println("It seems the deployment object already exists - retrieving latest version and updating it")
        (k8s get[Deployment] nginxDeployment.name) flatMap { curr =>
          println("retrieved latest deployment, now updating")
          val updated = nginxDeployment.withResourceVersion(curr.metadata.resourceVersion)
          k8s update updated
        }
      }
    }
  }
  
  def updateNginx(version: String): Future[Deployment] = {
    
    val updatedContainer = Container("nginx",image="nginx:" + version).exposePort(80)
    val currentDeployment = k8s get[Deployment] "nginx-deployment"
    
    currentDeployment flatMap { nginxDeployment =>
        val updatedDeployment = nginxDeployment.updateContainer(updatedContainer)
        k8s update updatedDeployment
    }  
  }
}
