package skuber.examples.guestbook

import skuber._
import skuber.json.format._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import akka.pattern.pipe
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.collection._
import play.api.libs.iteratee.Iteratee

/**
 * A KubernetesProxyActor proxies all requests from the Guestbook actors to Kubernetes. It is a slim wrapper 
 * around the skuber API, and enables sharing of skuber resources (e.g. underlying WS client 
 * connections, watches etc.) by all calling actors.
 * It supports request messages to create/delete/get Service and Replication Controller
 * resources on Kubernetes.
 * For each of the above the actor simply creates and invokes a skuber request, and pipes the 
 * (future) response back to a result handler actor specified in the request message.
 * 
 * It also supports a WatchReplicationController message that puts a reactive watch on a specified 
 * replication controller that forwards any updates received via the watch to one or more 
 * a specified actor. Multiple actors may watch the same controller - they reuse the same underlying watch. 
 * These watches are used by ScalerActor for monitoring the progress of scaling up/down of 
 * Guestbook services on the cluster.
 * 
 * @author David O'Riordan
 */
object KubernetesProxyActor {
  // messages we accept
  sealed abstract trait KubernetesRequestMessage
  case class DeleteService(name: String, resultHandler: ActorRef) extends KubernetesRequestMessage
  case class DeleteReplicationController(name: String, resultHandler: ActorRef) extends KubernetesRequestMessage
  case class CreateService(serviceSpec: Service, resultHandler: ActorRef) extends KubernetesRequestMessage
  case class CreateReplicationController(rcSpec: ReplicationController, resultHandler: ActorRef) extends KubernetesRequestMessage
  case class GetReplicationController(name: String, resultHandler: ActorRef) extends KubernetesRequestMessage
  case class UpdateReplicationController(newSpec: ReplicationController, resultHandler: ActorRef) extends KubernetesRequestMessage  
  case class WatchReplicationController(rc: ReplicationController, watcher: ActorRef) extends KubernetesRequestMessage  
  case class UnwatchReplicationController(rc: ReplicationController, watcher: ActorRef) extends KubernetesRequestMessage
  case object Close extends KubernetesRequestMessage

  case object Closed // response to Close request
  case object ResourceNotFound // return this if the target resource does not exist
}

class KubernetesProxyActor extends Actor with ActorLogging {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val k8s = k8sInit // initialize skuber client (request context)
  var rcWatching = mutable.HashMap[String, Watching]()
   
  private def invoke(skuberRequest: => Future[Any]) : Future[Any] = {   
    val reply = skuberRequest recover {
      case k8ex: K8SException if (k8ex.status.code.get==404) => {
        log.debug("resource not found on Kubernetes")        
        KubernetesProxyActor.ResourceNotFound 
      }
    }
    reply onSuccess { 
      case msg => log.debug("Kubernetes proxy returning: " + msg)
    } 
    reply onFailure { 
      case k8ex: K8SException => log.error("Kubernetes API returned failure...status = " + k8ex.status.code)
    }
    reply
  }
  
  import KubernetesProxyActor._
  
  def receive = LoggingReceive {
    case DeleteService(name,resultHandler) => invoke(k8s delete[Service] name) pipeTo resultHandler
    case DeleteReplicationController(name, resultHandler) => invoke(k8s delete[ReplicationController] name) pipeTo resultHandler
    case CreateService(serviceSpec, resultHandler) =>  invoke(k8s create[Service] serviceSpec) pipeTo resultHandler
    case CreateReplicationController(rcSpec, resultHandler) => invoke(k8s create[ReplicationController] rcSpec) pipeTo resultHandler
    case GetReplicationController(name: String, resultHandler) => invoke(k8s get[ReplicationController] name) pipeTo resultHandler
    case UpdateReplicationController(newSpec, resultHandler) => invoke(k8s update[ReplicationController] newSpec) pipeTo resultHandler
    
    case WatchReplicationController(rc: ReplicationController, watcher: ActorRef) => { 
      val currentlyWatching = rcWatching.get(rc.name)
      currentlyWatching match {
        case Some(watching) => {
          // already watching this RC - just add the watcher to the set of watching actors
          log.debug("Controller '" + rc.name +"' is already beng wateched - adding new watcher " + watcher.path)
          val newWatching = watching.copy(watchers = watching.watchers + watcher)
          rcWatching.put(rc.name, newWatching)
        }
        case None => {
          // not yet watching this controller
          // create a new watch on Kubernetes, and initialize the set of watchers on it
          
          log.debug("creating a watch on Kubernetes for controller + '" + rc.name + "', watcher is " + watcher.path )
          val watch = k8s watch rc
          val watching = Set(watcher)
          rcWatching += rc.name -> Watching(watch, watching)
          
          // this iteratee simply sends any updated RC objects received via the watch
          // on to all watchers
          
          watch.events run Iteratee.foreach { rcUpdateEvent =>
              rcWatching.get(rc.name).foreach { _.watchers.foreach { _ ! rcUpdateEvent._object } }  
          }
        }
      }
    }
    
    case UnwatchReplicationController(rc: ReplicationController, watcher: ActorRef) => {
      rcWatching.get(rc.name).foreach { watching =>
        val newWatchers = watching.watchers - watcher
        log.debug("removing watcher on '" + rc.name + "'")
        rcWatching.put(rc.name, watching.copy(watchers=newWatchers))
      }
    }
    
    case Close => {
      rcWatching foreach { case (_, watching) => 
        watching.watch.terminate
      }
      k8s.close
      System.out.println("Closed skuber client")
      sender ! Closed
    }
  }  
}

case class Watching(watch: K8SWatch[K8SWatchEvent[ReplicationController]], watchers: Set[ActorRef])