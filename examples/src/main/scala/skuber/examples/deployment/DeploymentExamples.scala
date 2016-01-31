package skuber.examples.deployment

import skuber._
import skuber.json.format._
import skuber.ext._
import skuber.json.ext.format._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author David O'Riordan
 * 
 * Some simple examples of using the Deployment object support in the extensions API to 
 * deploy a simple app
 */
object DeploymentExamples extends App {
 
  deployNginx
  
  def deployNginx = {
    
    val nginxLabel = "app" -> "nginx"
    val nginxContainer = Container("nginx",image="nginx:1.7.9").port(80)
    
    val nginxTemplate = Pod.Template.Spec
      .named("nginx")
      .addContainer(nginxContainer)
      .addLabel(nginxLabel)
        
    val desiredCount = 5  
    val nginxDeployment = Deployment("nginx-deployment")
      .withReplicas(desiredCount)
      .withTemplate(nginxTemplate)
    
    import scala.concurrent.ExecutionContext.Implicits.global
    val k8s = k8sInit
  
    println("Creating nginx deployment")
    val createdDeplFut = k8s create nginxDeployment
   
    val deplFut = createdDeplFut recoverWith {
      case ex: K8SException if (ex.status.code.contains(409)) => {
        println("It seems the deployment object already exists - retrieving latest version")
        k8s get[Deployment] nginxDeployment.name
      }
    }

    def updatedCount(depl: Deployment) = depl.status.get.updatedReplicas
    def reportProgress(depl: Deployment) = println("Updated replicas: " + updatedCount(depl) + " of " + desiredCount)
    def checkDone(depl: Deployment) : Boolean = {
      reportProgress(depl)
      updatedCount(depl)==desiredCount
    }
    
    val watchUntilDone = deplFut map { deployment =>
      if (checkDone(deployment))
        k8s.close
      else {     
        import play.api.libs.iteratee.Iteratee
        val deploymentProgressWatch = k8s watch deployment
        deploymentProgressWatch.events run Iteratee.foreach { deploymentProgressEvent =>
          if (checkDone(deploymentProgressEvent._object)) {
            deploymentProgressWatch.terminate()
            k8s.close
          }
        }
      }
    }  
  }
}