package skuber.examples.watch

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import skuber._
import skuber.json.format._

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.Iteratee

/**
 * @author David O'Riordan
 */
object WatchExamples {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  
  def  watchFrontEndScaling = {
     val k8s = k8sInit    
     val frontendFetch = k8s get[ReplicationController] "frontend"
     frontendFetch onSuccess { case frontend =>
       val frontendWatch = k8s watch frontend
       frontendWatch.events |>>> Iteratee.foreach { frontendEvent => println("Current frontend replicas: " + frontendEvent._object.status.get.replicas) }
     }     
     Thread.sleep(30000) // watch for some time before closing the session
     k8s close
  }
  
  def watchPodPhases = {
     val k8s = k8sInit    
     
     // watch from the latest Pod resource version i.e. only future Pod events will be enumerated
     // (without specifying a resource version all historic ones would be enumerated)
     // To get the current resource version we obtain the latest list of pods
     // The resource version is in the metadata returned with the list
     val currPodList = k8s list[PodList]()
     
     currPodList onSuccess { case pods =>
       val latestPodVersion = pods.metadata.map { _.resourceVersion } 
       val podWatch = k8s watchAll[Pod](sinceResourceVersion=latestPodVersion) 
       
       podWatch.events |>>> Iteratee.foreach { podEvent => 
         val pod = podEvent._object
         val phase = pod.status flatMap { _.phase }
         println(podEvent._type + " => Pod '" + pod.name + "' .. phase = " + phase.getOrElse("<None>"))    
       }
     }
    Thread.sleep(30000) // watch for some time before closing the session
    k8s close
  }
}