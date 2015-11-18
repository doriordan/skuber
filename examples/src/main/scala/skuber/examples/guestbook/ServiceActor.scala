package skuber.examples.guestbook

import skuber.model.ReplicationController
import skuber.model.Service
import skuber.model.Service.Type._
import skuber.api.client.K8SException

import akka.actor.{Actor, ActorRef}
import akka.actor.Props
import akka.util.Timeout
import akka.pattern.{ask,pipe}

import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

import model.GuestbookServiceSpecification
import ServiceActor._

/*
 * A service actor manages a single Guestbook service, encapsulating access to both the service
 * and its replication controller on Kubernetes
 * It supports creation and removal of service resources on Kubernetes, as well as
 * - scaling a service to a specified number of replicas
 * - stopping a service, which scales its replicas down to zero
 */
object ServiceActor {
  
  sealed abstract trait ServiceMessage
  case object Create
  case object Remove extends ServiceMessage
  case class Scale(n: Int)
  case object Stop
  
  sealed abstract trait ServiceReply
  case object ServiceRemoved extends ServiceReply
  case class ServiceScaledTo(n: Int) extends ServiceReply
  case object ServiceStopped extends ServiceReply
  case object ServiceCreated extends ServiceReply
  case object ServiceNotExists extends ServiceReply
  case object ResourceNotFound extends ServiceReply
  case class UnexpectedServiceError(name: String, ex: Throwable) extends ServiceReply
  
  def props(kubernetes: ActorRef, spec: GuestbookServiceSpecification) = Props(new ServiceActor(kubernetes, spec))
}

class ServiceActor(kubernetes: ActorRef, specification: GuestbookServiceSpecification) extends Actor {
  
  import ScalerActor._
  import KubernetesProxyActor._
  
  implicit val timeout = Timeout(30 seconds)
  
  /*
   * Remove a service
   * Delete first the service then controller
   */
  private def remove: Future[Any] =
    for {
      svc <- ask(kubernetes, DeleteService(specification.serviceName))
      rc  <- ask(kubernetes, DeleteReplicationController(specification.serviceName))
    } yield (svc,rc)
  
  /*
   * Stop a service - this is done by scaling the number of replicas down to zero
   */
  private def stop: Future[Any] = scale(0)
    
  private def scale(to: Int): Future[Any] = {
      
    import ScalerActor._
    val scaler = context.actorOf(props(kubernetes, specification.serviceName, to))
    ask(scaler, InitiateScaling) 
  }   
  
  private def create : Future[Any]= {
    val k8sResources = specification.buildKubernetesResources
    for {
      rc   <- ask(kubernetes, CreateReplicationController(k8sResources.rc)).mapTo[ReplicationController]
      svc  <- ask(kubernetes,CreateService(k8sResources.service)).mapTo[Service]
    } yield (rc, svc)
  }
  
  private def containsNotFoundResult(result: Any) : Boolean = result match {
    case ResourceNotFound | 
         (ResourceNotFound, ResourceNotFound) | 
         (ResourceNotFound,_) | 
         (_, ResourceNotFound) => true
    case other => false  
  }
  
  override def receive = {
    
    case Create => {
      val requester = sender
      val reply = create collect {
        case akka.actor.Status.Failure(ex) => UnexpectedServiceError(specification.serviceName, ex)
        case rnf if (containsNotFoundResult(rnf)) => 
          UnexpectedServiceError(specification.serviceName, new Exception("Not Found"))
        case other => ServiceCreated
      }
      reply map{ requester ! _ }
    }
    
    case Remove => {
      val requester = sender
      val reply = remove collect {
        case akka.actor.Status.Failure(ex) => UnexpectedServiceError(specification.serviceName, ex)
        case other => ServiceRemoved
      }
      reply map { requester ! _ }
    }
    
    case Scale(n: Int) =>  {
      val requester = sender
      val reply = scale(n) collect {
        case ScalingError => 
          UnexpectedServiceError(specification.serviceName,new Exception("An error occured while scaling"))
        case akka.actor.Status.Failure(ex) => UnexpectedServiceError(specification.serviceName, ex)
        case rnf if (containsNotFoundResult(rnf)) => 
          UnexpectedServiceError(specification.serviceName, new Exception("Unable to scale as resource does not exist"))
        case other => ServiceScaledTo(n)
      }
      reply map { requester ! _ }
    }
    
    case Stop => {
      val requester = sender
      val reply = stop collect {
        case ScalingError => 
          UnexpectedServiceError(specification.serviceName,new Exception("An error occured while scaling"))
        case akka.actor.Status.Failure(ex) => UnexpectedServiceError(specification.serviceName, ex)
        case other => ServiceStopped
      }
      reply map { requester ! _ }
    }
  }  
}