package skuber.examples.guestbook

import skuber.api.client._
import skuber.model._
import skuber.model.coretypes._
import skuber.json.format._

import akka.actor.{Actor, ActorRef}
import akka.actor.Props
import akka.event.Logging
import akka.pattern.pipe

import scala.concurrent.Future
import scala.util.{Success, Failure}

import play.api.libs.iteratee.Iteratee

/**
 * A KubernetesProxyActor proxies all requests from the Guestbook actors to Kubernetes. It is a slim wrapper around
 * the skuber API. The benefit the proxy provides is mainly sharing of the underlying request context connections/
 * resources, as all Guestbook actors proxy through the same actor instance.
 * The following messages / requests are supported 
 * - DeleteService
 * - DeleteReplicationController
 * - CreateService
 * - CreateReplicationController
 * - UpdateReplicationController
 * For each of the above the actor simply makes the appropriate Kubernetes API request via skuber and pipes the 
 * response (or failure) back to the sender unmodified.
 * It also supports a WatchReplicationController message that puts a watch on a given controller and just
 * forward any events to the message sender. This is used by the Scaler actor for monitoring the progress of scaling
 * up/down of services
 * 
 * @author David O'Riordan
 */
object KubernetesProxyActor {
  // messages we accept
  sealed abstract trait KubernetesRequestMessage
  case class DeleteService(name: String) extends KubernetesRequestMessage
  case class DeleteReplicationController(name: String) extends KubernetesRequestMessage
  case class CreateService(serviceSpec: Service) extends KubernetesRequestMessage
  case class CreateReplicationController(rcSpec: ReplicationController) extends KubernetesRequestMessage
  case class GetReplicationController(name: String) extends KubernetesRequestMessage
  case class UpdateReplicationController(newSpec: ReplicationController) extends KubernetesRequestMessage  
  case class WatchReplicationController(rc: ReplicationController) extends KubernetesRequestMessage  
  case object Close extends KubernetesRequestMessage
  case object ResourceNotFound // return this if the target resource does not exist
}

class KubernetesProxyActor extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global
  val k8s = k8sInit // creates a skuber request context using configured defaults 
  
  import KubernetesProxyActor._
   
  private def perform(request: => Future[Any]) : Future[Any] = {   
    val requester = sender
    val reply = request recover {
      case k8ex: K8SException if (k8ex.status.code.get==404) => ResourceNotFound 
    }
    reply onSuccess { 
      case msg => System.out.println("Kubernetes Proxy Returning: " + msg)
    } 
    reply onFailure { 
      case k8ex: K8SException => System.err.println("Kubernetes API returned failure, status = " + k8ex.status.code)
    }
    reply pipeTo requester
  }
  
  def receive = {
    case DeleteService(name) => perform(k8s delete[Service] name)
    case DeleteReplicationController(name) => perform(k8s delete[ReplicationController] name)
    case CreateService(serviceSpec) => perform(k8s create[Service] serviceSpec)
    case CreateReplicationController(rcSpec) => perform(k8s create[ReplicationController] rcSpec)
    case GetReplicationController(name: String) => perform(k8s get[ReplicationController] name)
    case UpdateReplicationController(newSpec) => perform(k8s update[ReplicationController] newSpec)
    
    case WatchReplicationController(rc: ReplicationController) => {
      val watcher = sender  
      val rcEventEnumerator = k8s watch rc 
      rcEventEnumerator apply Iteratee.foreach { rcEvent=>
        System.out.println("Kubernetes Proxy sending update to watching actor: " + rcEvent._object)
        watcher ! rcEvent._object }
    }
    
    case Close => k8s close
  }  
}