package skuber.examples.guestbook

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Try, Success,Failure}

import akka.actor.{Actor, ActorRef, ActorLogging}
import akka.actor.Props
import akka.event.{LoggingReceive}
import akka.pattern.ask
import akka.util.Timeout

import model.GuestbookServiceSpecification

object GuestbookActor {
  
  case object Deploy // the message this actor receives
  
  // replies
  case object DeployedSuccessfully
  case class DeploymentFailed(ex: Throwable)
  
   // Some simple specifications for the service actors we will create
  val redisMasterSpec: GuestbookServiceSpecification = GuestbookServiceSpecification(serviceName="redis-master",
        containerName="master",
        image="redis", 
        containerPort=6379, 
        replicas=1)
  
  val redisSlaveSpec: GuestbookServiceSpecification = GuestbookServiceSpecification(serviceName="redis-slave",
        containerName="worker", 
        image="kubernetes/redis-slave", 
        containerPort=6379, 
        replicas=2)
  
  val frontEndSpec: GuestbookServiceSpecification =GuestbookServiceSpecification(serviceName="frontend",
        containerName="php-redis",
        image="kubernetes/example-guestbook-php-redis", 
        containerPort=80, 
        replicas=3, 
        serviceType=skuber.Service.Type.NodePort, 
        nodePort=30291)   
}

/**
 * @author David O'Riordan
 * This actor is responsible for overall orchestration of the deployment of the Guestbook application to Kubernetes
 */
class GuestbookActor extends Actor with ActorLogging {
   
  import GuestbookActor._
  import ServiceActor._
  import KubernetesProxyActor.ResourceNotFound
  
  // Create the other actors supporting the deployment
  val kubernetesProxy: ActorRef = context.actorOf(Props[KubernetesProxyActor](), "kubernetes")
  val redisMasterService: ActorRef = context.actorOf(ServiceActor.props(kubernetesProxy, redisMasterSpec), "redisMaster")
  val redisSlaveService: ActorRef = context.actorOf(ServiceActor.props(kubernetesProxy, redisSlaveSpec), "redisSlave")
  val frontEndService: ActorRef = context.actorOf(ServiceActor.props(kubernetesProxy, frontEndSpec), "frontEnd")
 
  // set up defaults for actor messaging
  implicit val timeout: Timeout = Timeout(20.seconds)
  
  var redisMasterRunning=false
  var redisSlaveRunning=false
  var frontEndRunning=false
  
  var requester: ActorRef = sender()
  
  // A simple wrapper of requests to the Guestbook service actors, ensuring the returned future
  // fails with an appropriate exception if error / unexpected reply is received. This makes
  // chaining the the requests together in an overall deployment process simpler as we only need to check 
  // for failure once at the end of the chain.
  private def askService(service: ActorRef, msg: Any, successInfo: String) = {
    val reply = ask(service, msg)
    
    reply onComplete { 
      case Success(msg) => log.debug("successfully received reply from service: " + msg) 
      case Failure(ex) => log.error("Asking service actor " + service.path + " failed with: " + ex)
    }
    reply collect { 
      case UnexpectedServiceError(name, ex) => throw ex
      case success => {
        System.out.println(successInfo)
        success
      }
    }
  }
  
  // deployment orchestration step definitions
  // each step returns a Future that completes when the step is finished
  
  // STEP 1    
  // In case the services exist already we first stop them running - this prepares them to be
  // removed in the housekeeping step
  def stop: Future[Any] = for {
     _ <-    askService(frontEndService, Stop, "Front-end service stopped")
     _ <-    askService(redisSlaveService, Stop, "Redis slave service stopped")
     done <- askService(redisMasterService, Stop, "Redis master service stopped")
  } yield done
  
  
  
  // STEP 2 overall housekeeping step that removes each Guestbook service from Kubernetes, if it exists
  // If one or more services do not exist at this stage, it simply ignores the NotFound error(s) 
  // and continues.
  def housekeep: Future[Any] = for {
     _ <-    askService(frontEndService, Remove, "Front-end service & replication controller from previous deployment(s) have been removed (if they existed)")
     _ <-    askService(redisSlaveService, Remove, "Redis slave service & replication controller from previous deployment(s) have been removed (if they existed)")
     done <- askService(redisMasterService, Remove, "Redis master service & replication controller from previous deployment(s) removed (if they existed)")
  } yield done
  
  // STEP 3 (re)create the service resources on Kubernetes in the appropriate order
  def create: Future[Any] = {
    System.out.println("*** (waiting 5 seconds to allow any previous housekeeping to complete on server...) ***")
    Thread.sleep(5000)
    for {
      _ <-    askService(redisMasterService, Create, "Front-end service & replication controller (re)created")
      _ <-    askService(redisSlaveService, Create, "Redis slave service & replication controller (re)created")
      done <- askService(frontEndService, Create, "Redis master service & replication controller (re)created")
    } yield done
  }
    
  // STEP 4 This step completes when all replicas are running
  // To get notified if/when they are all running we ask each service to scale to the
  // required count - each service then eventually replies when that count has been 
  // reached
  def ensureAllRunning: Future[Any] = for {
     _ <- askService(redisMasterService, Scale(redisMasterSpec.replicas), "All Redis master replicas are now running")
     _ <- askService(redisSlaveService, Scale(redisSlaveSpec.replicas), "All Redis slave replicas are now running")
     done <- askService(frontEndService, Scale(frontEndSpec.replicas), "All front-end replicas are now running")
  } yield done

  def receive: Receive = LoggingReceive {
    // perform a requested deployment by calling the high level steps above in turn
    case Deploy => {
      System.out.println("Deploying Guestbook application to Kubernetes.\nThis involves four steps:\n=> stopping the Guestbook services if they are running (by specifying replica counts of 0)\n=> housekeeping the Guestbook application (i.e. removing the resources from Kubernetes if they exist)\n=> (re)creating the Guestbook application on Kubernetes\n=> validating that all replicas are running\n")
      requester = sender()
      log.debug("Received Deploy instruction")
      System.out.println("*** Now stopping services (if they already exist and are running)\n")
      val deploy = for {
        _    <- stop
        i1 = System.out.println("\n*** Now removing previous deployment (if necessary)\n")
        _    <- housekeep
        i2 = System.out.println("\n*** Now (re)creating the services and replication controllers on Kubernetes\n")
        _    <- create
        i3 = System.out.println("\n*** Now validating that all replicas are running - if required reactively watch status until done\n")
        done <- ensureAllRunning
      } yield done
      
      deploy onComplete { case _ => ask(kubernetesProxy, KubernetesProxyActor.Close)(timeout) }
      deploy onComplete {
        case Success(_) => requester ! DeployedSuccessfully
        case Failure(ex) => requester ! DeploymentFailed(ex)
      }
    }
  }  
}
