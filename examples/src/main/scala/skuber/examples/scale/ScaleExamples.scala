package skuber.examples.scale

import skuber._
import skuber.json.format._

// import extensions API to access scale functionality
import skuber.ext._
import skuber.json.ext.format._

/**
 * @author David O'Riordan
 * 
 * Some simple examples of using the extensions API to scale up and down a Replication Controller
 * or Deployment resource.
 */
object ScaleExamples {
 
  def scaleNginxController = {
    
    val nginxSelector  = Map("app" -> "nginx")
    val nginxContainer = Container("nginx",image="nginx").port(80)
    val nginxController= ReplicationController("nginx",nginxContainer,nginxSelector).withReplicas(5)
   
    import scala.concurrent.ExecutionContext.Implicits.global
    val k8s = k8sInit
  
    println("Creating nginx replication controller")
    val createdRCFut = k8s create nginxController
   
    val rcFut = createdRCFut recoverWith {
      case ex: K8SException if (ex.status.code.contains(409)) => {
        println("It seesm the controller already exists - retrieving latest version")
        k8s get[ReplicationController] nginxController.name
      }
    }
    val directlyScale = for {
        rc <- rcFut
        _ = println("Directly Scaling replication controller down to 1 replica")
        scaledDown <- k8s.scale(rc, 1)
        _ = println("Scale object returned: specified = " + scaledDown.spec.replicas + ", current = " + scaledDown.status.get.replicas)
        _ = println("Now directly scaling it up to 4 replicas")
        scaledUp   <- k8s.scale(rc,4)
        _ = println("Scale object returned: specified = " + scaledUp.spec.replicas + ", current = " + scaledUp.status.get.replicas)   
    } yield rc
    
    val autoScale = for {
      rc   <- directlyScale 
      hpas <-  { 
        println("Now creating a HorizontalPodAutoscaler to automatically scale the replicas")
        val hpas = HorizontalPodAutoscaler.scale(rc).
                     withMinReplicas(2).
                     withMaxReplicas(8).
                     withCPUTargetUtilization(80)
        k8s create[HorizontalPodAutoscaler] hpas  
      }
      _ = println("Successfully created horizontal pod autoscaler")
    } yield hpas
    
    autoScale onComplete { case _ => k8s.close }
    
  } 
}