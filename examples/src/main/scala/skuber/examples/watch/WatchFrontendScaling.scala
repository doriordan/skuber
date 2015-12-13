package skuber.examples.watch

import skuber._
import skuber.json.format._
  
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.iteratee.Iteratee

/**
 * @author David O'Riordan
 */
object WatchFrontendScaling {
  def run = {
     val k8s = k8sInit    
     val frontendFetch = k8s get[ReplicationController] "frontend"
     frontendFetch onSuccess { case frontend =>
       val frontendWatch = k8s watch frontend
       frontendWatch.events |>>> Iteratee.foreach { frontendEvent => println("Current frontend replicas: " + frontendEvent._object.status.get.replicas) }
     }     
  }
}