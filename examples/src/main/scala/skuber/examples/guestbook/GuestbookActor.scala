package skuber.examples.guestbook

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.Try
import scala.annotation.tailrec
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.actor.Props
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout

import model.GuestbookServiceSpecification

object GuestbookActor {
  
  case object Deploy // the message this actor receives
  
  // replies
  case object DeployedSuccessfully
  case class DeploymentFailed(ex: Throwable)
  
   // Some simple specifications for the service actors we will create
  val redisMasterSpec = GuestbookServiceSpecification(
        serviceName="redis-master",
        containerName="master",
        image="redis", 
        containerPort=6379, 
        replicas=1)
  
  val redisSlaveSpec = GuestbookServiceSpecification(
        serviceName="redis-slave", 
        containerName="worker", 
        image="kubernetes/redis-slave:v2", 
        containerPort=6379, 
        replicas=2)
  
  val frontEndSpec=GuestbookServiceSpecification(
        serviceName="frontend", 
        containerName="php-redis",
        image="kubernetes/example-guestbook-php-redis:v2", 
        containerPort=80, 
        replicas=3, 
        serviceType=skuber.model.Service.Type.NodePort, 
        nodePort=30291)   
}

/**
 * @author David O'Riordan
 * This actor is responsible for overall orchestration of the deployment of the Guestbook application to Kubernetes
 */
class GuestbookActor extends Actor {
   
  import GuestbookActor._
  import ServiceActor._
  import KubernetesProxyActor.ResourceNotFound
  
  // Create the other actors supporting the deployment
  val kubernetesProxy = context.actorOf(Props[KubernetesProxyActor]) 
  val redisMasterService = context.actorOf(ServiceActor.props(kubernetesProxy, redisMasterSpec))
  val redisSlaveService = context.actorOf(ServiceActor.props(kubernetesProxy, redisSlaveSpec))
  val frontEndService = context.actorOf(ServiceActor.props(kubernetesProxy, frontEndSpec))
 
  // set up defaults for actor messaging
  implicit val timeout = Timeout(60 seconds) 
  
  // simple helper for wrapping requests to the guestbook service actors, ensuring the returned future
  // fails with an appropriate exception if error / unexpected reply is received
  private def askService(service: ActorRef, msg: Any, okIfNotFound: Boolean=false) = {
    val reply = ask(service, msg)
    reply foreach { r => System.out.println("Service reply: " + r) }
    reply collect { 
      case UnexpectedServiceError(name, ex) => throw ex
      case other => other
    }
  }
  
  // deployment orchestration step definitions
  // each step returns a Future that completes when the step is finished
  
  // STEP 1    
  // Housekeep each service on Kubernetes
  // We do this by first stopping it and then removing the service resources (if they exist)
    
  def housekeep(service: ActorRef) = for {
    _    <- askService(service, Stop)
    done <- askService(service, Remove)
  } yield done
   
  // overall housekeeping step that orchestrates the above, executing them in order
  // starting with front end
  def housekeepResources = for {
    _    <- housekeep(frontEndService)
    _    <- housekeep(redisSlaveService)
    done <- housekeep(redisMasterService)
  } yield done
  
  // STEP 2
  // (re)create resources for each service on the cluster
  def createRedisMaster = askService(redisMasterService, Create)
  def createRedisSlave = askService(redisSlaveService, Create)
  def createFrontEnd = askService(frontEndService, Create)
  
  // overall create step that orchestrates the above, creating the resources in the appropriate order
  def createResources = for {
    _ <- createRedisMaster
    _ <- createRedisSlave
    done <- createFrontEnd
  } yield done
    
  // STEP 3 Completes when all replicas are running. To know when all replicas are running
  // we ask the service to scale to the expected count and expect to receive ScalingDone
  // when that is complete
  
  def redisMasterRunning = askService(redisMasterService, Scale(redisMasterSpec.replicas))
  def redisSlaveRunning = askService(redisSlaveService, Scale(redisSlaveSpec.replicas))
  def frontEndRunning = askService(frontEndService, Scale(frontEndSpec.replicas))
  def allRunning = for {
     _ <- redisMasterRunning 
     _ <- redisSlaveRunning
     done <- frontEndRunning
  } yield done

  def receive = {
    // perform a requested deployment by calling the main three steps above in turn
    case Deploy => {
      val requester = context.sender
      val doDeployment = for {
        _ <- housekeepResources
        _ <- createResources
        done <- allRunning
      } yield done
      doDeployment map { 
        d => requester ! DeployedSuccessfully 
        kubernetesProxy ! KubernetesProxyActor.Close
      }
      doDeployment recover {
        case ex => {
          requester ! DeploymentFailed(ex)
          kubernetesProxy ! KubernetesProxyActor.Close
        }
      }
    }
  }
}