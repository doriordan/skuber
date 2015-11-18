package skuber.examples.guestbook

import skuber.model.ReplicationController
import skuber.api.client.WatchEvent

import akka.actor.{Actor, ActorRef}
import akka.actor.Props
import akka.event.Logging
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.Status.Failure

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

import akka.actor.Status.Failure

/**
 * The scaler actor is responsible for scaling a replica count up/down for a Guestbook service
 * One scaler actor handles a single scaling request
 */

object ScalerActor {
  
  case object InitiateScaling  
  case class ScalingDone(controllerName: String, toReplicaCount: Int) // sent to the parent when finished
  case class ScalingError(ex: Failure)
  
  def props(kubernetes: ActorRef, controllerName: String, targetReplicaCount: Int) = 
    Props(new ScalerActor(kubernetes, controllerName, targetReplicaCount))
}

import ScalerActor._

class ScalerActor(kubernetes: ActorRef, controllerName: String, targetReplicaCount: Int) extends Actor {
  
  import ScalerActor._
  import KubernetesProxyActor._
  
  private def isScalingComplete(rc: ReplicationController) = rc.status.get.replicas == targetReplicaCount
  private def specNeedsChanging(rc: ReplicationController) = rc.spec.get.replicas != targetReplicaCount

  private def report(s: String) = System.err.println("Scale(" + controllerName + "," + targetReplicaCount + "): " + s)
  private def reportStatus(rc: ReplicationController) = 
    report(rc.status.get.replicas + " of " + rc.spec.get.replicas + " running")
    
  implicit val timeout = Timeout(60 seconds) 
   
  // Scaling will proceed through initial, updating, (possibly) watching, and completed behaviours
  //
  // initial: receives initiate scaling request: asks Kubernetes for latest RC data and moves to  watching
  // specify: receives initial RC data from Kubernetes, posting an update to the number of 
  // replicas in its specification if necessary. If/when the spec is as required, it then either completes (if all replicas 
  // status indicates correct replica count already running) or moves to watching 
  // watch: specification update is now complete, so passively watch RC updates from Kubernetes until status 
  // indicates count of replicas running matches the specification
  // completed: scaling is done or a failure has happened - parent has been notified so there is no more 
  // to do.
  
  def receive = initial 
  var requester = context.sender
  
  def initial : Receive = {
     case InitiateScaling => {
       requester = sender
       // ask Kubernetes for current RC and switch to specify behaviour to handle the response
       report("Initiating...")
       kubernetes ! GetReplicationController(controllerName) 
       context.become(specify) 
     }
  } 
  
  // each behavior chains together component receive handlers in order of precedence - once one matches
  // the remaining ones won't be called
  def specify : Receive = maybeNotFound orElse changeSpecIfNecessary orElse completeIfScalingDone orElse gotoWatching orElse handleFailureStatus
  def watch: Receive = completeIfScalingDone orElse keepWatching orElse handleFailureStatus  
  def completed: Receive = { case _ => } // just discard all messages if completed scaling
 
  def maybeNotFound : Receive = {
    // the first request on the RC may return a ResourceNotFound exception, propagate to the parent
    // to handle and end the scaling attempt
    case KubernetesProxyActor.ResourceNotFound => {
      report("replication controller does not exist - nothing to scale")
      requester ! KubernetesProxyActor.ResourceNotFound
      context.become(completed)
    }
  }
  
  def changeSpecIfNecessary: Receive = {
      case rc: ReplicationController if (specNeedsChanging(rc)) => {
        report("Asking Kubernetes to change specified replica count to " + targetReplicaCount)
        kubernetes ! UpdateReplicationController(rc.withReplicas(targetReplicaCount))
      }
  }
  def completeIfScalingDone: Receive = {  
    case rc: ReplicationController if (isScalingComplete(rc)) => {
      reportStatus(rc)
      report("Scaling successfully completed")
      done
    }
  }
  def gotoWatching: Receive = {
    case rc: ReplicationController => {
      reportStatus(rc)
      report("Placing a watch on the RC to receive updates to the running replica count")
      kubernetes ! WatchReplicationController(rc) 
      context.become(watch)
    }
  }
  def keepWatching: Receive = {
    case rc: ReplicationController => reportStatus(rc)
  }
  
  def handleFailureStatus: Receive = {
    case fail: Failure => error(fail) // probably an error response from Kubernetes
    case msg => report("Received unexpected message: " + msg)
  }
  
  // handle successful or failed completion of scaling, notifying parent
  def done = {
    requester ! ScalingDone(controllerName, targetReplicaCount)
    context.become(completed)
  }  
  def error(ex: Failure) = {
    report("Scaling ended with error")
    requester  ! ScalingError(ex)
    context.become(completed)
  }
}