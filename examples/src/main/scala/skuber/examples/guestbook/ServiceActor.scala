package skuber.examples.guestbook

import akka.actor.{Actor, ActorRef, ActorLogging}
import akka.actor.Props
import akka.util.Timeout
import akka.event.LoggingReceive

import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

import model.GuestbookServiceSpecification

/*
 * A service actor manages a single Guestbook service, encapsulating access to both the service
 * and its replication controller on Kubernetes
 * It supports creation and removal of service resources on Kubernetes, as well as
 * - scaling a service to a specified number of replicas
 * - stopping a service, which scales its replicas down to zero if the service exists
 */
object ServiceActor {
  
  sealed abstract trait ServiceMessage
  case object Create
  case object Remove extends ServiceMessage
  case class Scale(n: Int)
  case object Stop
  
  sealed abstract trait ServiceReply
  case object ServiceRemoved extends ServiceReply
  case class ServiceScaledTo(name: String, n: Int) extends ServiceReply
  case object ServiceStopped extends ServiceReply
  case object ServiceCreated extends ServiceReply
  case object ServiceNotExists extends ServiceReply
  case class UnexpectedServiceError(name: String, ex: Throwable) extends ServiceReply
  
  def props(kubernetes: ActorRef, spec: GuestbookServiceSpecification): Props = Props(new ServiceActor(kubernetes, spec))
}

import ServiceActor._

class ServiceActor(kubernetes: ActorRef, specification: GuestbookServiceSpecification) extends Actor with ActorLogging {
  
  implicit val timeout: Timeout = Timeout(60.seconds)
   
  /*
   * Create the service on Kubernetes
   * Creates both a service and associated replication controller based on the 
   * specification passed to the actors constructor
   * Replies with ServiceCreated (via a result handler) if all goes well
   */
  private def create: Unit = {
    import KubernetesProxyActor.{CreateReplicationController, CreateService}
    val k8sResources = specification.buildKubernetesResources
    val resultHandler = context.actorOf(CreateResultHandler.props(sender(), specification.serviceName))
    kubernetes ! CreateReplicationController(k8sResources.rc, resultHandler)
    kubernetes ! CreateService(k8sResources.service, resultHandler)
  }
  /*
   * Remove the service
   * Deletes both the service its replication controller on Kubernetes
   * Replies with ServiceRemoved (via a result handler) if all goes well
   */
  private def remove: Unit = {
    import KubernetesProxyActor.{DeleteReplicationController, DeleteService}
    val name = specification.serviceName
    val resultHandler = context.actorOf(RemoveResultHandler.props(sender(), name))
    kubernetes ! DeleteReplicationController(name, resultHandler)
    kubernetes ! DeleteService(name, resultHandler)
  }
    
  /*
   * Scale the number of replicas for the service to the specified count
   * Replies with ScalingDone (via a result handler) when target replica count reached
   */
  private def scale(to: Int): Unit = {
    import ScalerActor.InitiateScaling
    val name = specification.serviceName
    val scaler = context.actorOf(ScalerActor.props(kubernetes, name, to),"scale-to-" + to)
    val resultHandler = context.actorOf(ScaleResultHandler.props(sender(), specification.serviceName))
    scaler ! InitiateScaling(resultHandler) 
  }   
 
  /*
   * Stop the service - accomplished by scaling replicas down to zero
   * Replies with ServiceStopped (via a result handler) when all replicas stopped
   */
  private def stop: Unit = {
    import ScalerActor.InitiateScaling
    val name = specification.serviceName
    val scaler = context.actorOf(ScalerActor.props(kubernetes, name, 0),"stop")
    val resultHandler = context.actorOf(StopResultHandler.props(sender(), specification.serviceName))
    scaler ! InitiateScaling(resultHandler) 
  }   
  
  override def receive: Receive = LoggingReceive {
    case Create => create
    case Remove => remove
    case Scale(n) => scale(n)
    case Stop => stop
  }
}

/*
 * Per service request actors - each of these short-lived actors receives one or more 
 * responses from a supporting actor (the kubernetes proxy or a scaler) for a given 
 * specific service request. It then composes and sends the appropriate service 
 * response to the service consumer, and stops itself.
 */

abstract class ServiceResultHandler(serviceConsumer: ActorRef) extends Actor with akka.actor.ActorLogging { 
  def complete(response: Any): Unit = {
     log.debug("Sending service response " + response + " to " + serviceConsumer.path)
     serviceConsumer ! response
     context.stop(self)
  }
}

import KubernetesProxyActor.ResourceNotFound

object CreateResultHandler {
  def props(consumer: ActorRef, name: String): Props = Props(new CreateResultHandler(consumer, name))
}

class CreateResultHandler(consumer: ActorRef, name: String) extends ServiceResultHandler(consumer) {

  // Two create requests will have been sent to the Kubernetes proxy, so complete when 
  // the two expected results (created RC and service resources) have been received back
  var countResults = 0
  
  private def gotExpectedResult: Unit = {
      countResults += 1
      if (countResults==2)
        complete(ServiceCreated)
    }
  
  override def receive: Receive = LoggingReceive {
    case akka.actor.Status.Failure(ex) => complete(UnexpectedServiceError(name, ex))
    case ResourceNotFound => 
          complete(UnexpectedServiceError(name, new Exception("Not Found")))
    case r:skuber.ReplicationController => gotExpectedResult
    case s:skuber.Service => gotExpectedResult
  }
}

object RemoveResultHandler {
  def props(consumer: ActorRef, name: String): Props = Props(new RemoveResultHandler(consumer, name))
}

class RemoveResultHandler(consumer: ActorRef, name: String) extends ServiceResultHandler(consumer) {
  
  // Two delete requests will have been sent to the Kubernetes proxy, so complete when 
  // two non error results have been received back
  var countResults = 0
  override def receive: Receive = LoggingReceive {
    case akka.actor.Status.Failure(ex) => complete(UnexpectedServiceError(name, ex))
    case other => {
      countResults += 1
      if (countResults==2)
        complete(ServiceRemoved)
    }
  }
}

object ScaleResultHandler {
  def props(consumer: ActorRef, name: String): Props = Props(new ScaleResultHandler(consumer, name))
}

class ScaleResultHandler(consumer: ActorRef, name: String) extends ServiceResultHandler(consumer) {
  override def receive: Receive = LoggingReceive {
    case ScalerActor.ScalingError => complete(UnexpectedServiceError(name,new Exception("An error occured while scaling")))
    case akka.actor.Status.Failure(ex) => complete(UnexpectedServiceError(name, ex))
    case ResourceNotFound => complete(UnexpectedServiceError(name, new Exception("Unable to scale as resource does not exist")))
    case s: ScalerActor.ScalingDone => complete(ServiceScaledTo(name, s.toReplicaCount))
  }
}

object StopResultHandler {
  def props(consumer: ActorRef, name: String): Props = Props(new StopResultHandler(consumer, name))
}

class StopResultHandler(consumer: ActorRef, name: String) extends ServiceResultHandler(consumer) {
  override def receive: Receive = LoggingReceive {
    case ScalerActor.ScalingError => complete(UnexpectedServiceError(name,new Exception("An error occured while scaling")))
    case akka.actor.Status.Failure(ex) => complete(UnexpectedServiceError(name, ex))
    case ResourceNotFound => complete(ServiceStopped) // if service not exists treat as Stopped
    case s: ScalerActor.ScalingDone => complete(ServiceStopped)
  }
}