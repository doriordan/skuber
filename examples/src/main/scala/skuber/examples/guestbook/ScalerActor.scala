package skuber.examples.guestbook

import skuber.ReplicationController

import akka.actor.{Actor, ActorRef, ActorLogging}
import akka.actor.Props
import akka.event.{LoggingReceive}
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
  
  case class InitiateScaling(resultHandler: ActorRef) 
  case class ScalingDone(controllerName: String, toReplicaCount: Int) // sent to the parent when finished
  case class ScalingError(ex: Failure)
  
  def props(kubernetes: ActorRef, controllerName: String, targetReplicaCount: Int): Props =
    Props(new ScalerActor(kubernetes, controllerName, targetReplicaCount))
}

import ScalerActor._

class ScalerActor(kubernetes: ActorRef, controllerName: String, targetReplicaCount: Int) extends Actor with ActorLogging {
  
  import ScalerActor._
  import KubernetesProxyActor._
  
  private def isScalingComplete(rc: ReplicationController) = rc.status.get.replicas == targetReplicaCount
  private def specNeedsChanging(rc: ReplicationController) = rc.spec.get.replicas != targetReplicaCount

  private def report(s: String): Unit = System.out.println("  '" + controllerName + "' => " + s)
  private def reportStatus(rc: ReplicationController): Unit =
    report(rc.status.get.replicas + " replicas currently running (target: " + rc.spec.get.replicas + ")")
    
  implicit val timeout: Timeout = Timeout(60.seconds)
  
  var watching: Option[ReplicationController]= None
   
  // Scaling will proceed through initial, updating, (possibly) watching, and completed behaviours
  //
  // initial: receives initiate scaling request: asks Kubernetes for latest RC data and moves to updateSpecification
  // updateSpecification: receives initial RC data from Kubernetes, posting an update to the number of 
  // replicas in its specification if necessary. If/when the spec is as required, it then either completes (if all replicas 
  // already running) or moves to watching 
  // waitForCompletion: specification update is now complete, so passively watch RC updates from Kubernetes until status 
  // indicates number of replicas running matches the specification
  // completed: scaling is done or a failure has happened - parent has been notified so there is no more 
  // to do.
  
  def receive: Receive = initial
  var resultHandler: ActorRef = context.sender()
  
  def initial : Receive = LoggingReceive {
     case InitiateScaling(resultHandler: ActorRef) => {
       this.resultHandler = resultHandler
       // ask Kubernetes for current RC, handling the result via the updateSpecification behavior
       kubernetes ! GetReplicationController(controllerName, self) 
       context.become(updateSpecification) 
     }
  } 
  
  // each behavior chains together component receive handlers in order of precedence - once one matches
  // the remaining ones won't be called
  def updateSpecification : Receive = maybeNotFound orElse specNeedsUpdating orElse isCompleted orElse startWaiting orElse handleFailureStatus
  def waitForCompletion: Receive = isCompleted orElse isNotYetCompleted orElse handleFailureStatus  
  def completed: Receive = { case _ => } // just discard all messages if completed scaling
 
  def maybeNotFound : Receive = LoggingReceive {
    // the first request on the RC may return a ResourceNotFound exception, propagate to the parent
    // to handle and end the scaling attempt
    case KubernetesProxyActor.ResourceNotFound => {
      report("replication controller does not exist on Kubernetes - nothing to scale")
      resultHandler ! KubernetesProxyActor.ResourceNotFound
      context.become(completed)
    }
  }
  
  def specNeedsUpdating: Receive = LoggingReceive {
      case rc: ReplicationController if (specNeedsChanging(rc)) => {
        report("updating specified replica count on Kubernetes to " + targetReplicaCount) 
        val update = rc.withReplicas(targetReplicaCount)
        kubernetes ! UpdateReplicationController(update, self)
      }
  }
  
  def isCompleted : Receive = LoggingReceive {
    case rc: ReplicationController if (isScalingComplete(rc)) => {
      reportStatus(rc)
      done
    }
  }
  
  def startWaiting: Receive = LoggingReceive {
    case rc: ReplicationController => {
      reportStatus(rc)
      report("scaling in progress on Kubernetes - creating a reactive watch to monitor progress")
      kubernetes ! WatchReplicationController(rc, self) 
      watching = Some(rc)
      context.become(waitForCompletion)
    }
  }
  def isNotYetCompleted : Receive = LoggingReceive {
    case rc: ReplicationController => reportStatus(rc) // just continue to watch
  }
  
  def handleFailureStatus: Receive = LoggingReceive {
    case fail: Failure => error(fail) // probably an error response from Kubernetes
    case msg => report("received unexpected message: " + msg)
  }
  
  // handle successful or failed completion of scaling
  def done: Unit = {
    if (targetReplicaCount==0)
      report("successfully stopped all replica(s)")
    else
      report("successfully scaled to " + targetReplicaCount + " replica(s)")
          
    resultHandler ! ScalingDone(controllerName, targetReplicaCount)
    watching foreach { kubernetes ! UnwatchReplicationController(_, self) }
    watching = None
    context.become(completed)
  }  
  
  def error(ex: Failure): Unit = {
    report("scaling ended with error: " + ex.cause.getMessage)
    resultHandler ! ScalingError(ex)
    watching foreach { kubernetes ! UnwatchReplicationController(_, self) }
    watching = None
    context.become(completed)
  }
}