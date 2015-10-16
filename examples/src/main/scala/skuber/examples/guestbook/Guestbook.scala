package skuber.examples.guestbook

import skuber.api.client._
import skuber.model._
import skuber.model.coretypes._
import skuber.json.format._

import scala.concurrent._
import scala.concurrent.duration._

import scala.util.Try
import scala.annotation.tailrec

import java.util.UUID

/**
 * @author David O'Riordan
 */
object Guestbook extends App {
 
  import Service.Type._ 
  
  type ControllerEvent=WatchEvent[ReplicationController]
  
  // For each execution of this example we create a unique deployment run ID that will be added as a label and 
  // selector on the created replication controllers and their pods. This makes it easier to watch/filter K8S events 
  // specific to this run, which we use to reactively track the status of the resulting pods until they are all 
  // running.
  val deploymentRunKey =  "examples.skuber.io/deployment-run-id" 
  val deploymentRunID = UUID.randomUUID()

  
  // Some simple specs for the service resources we will create
  val redisMasterSpec = SimpleServiceSpec(
        serviceName="redis-master",
        containerName="master",
        image="redis", 
        containerPort=6379, 
        replicas=1)
  
  val redisSlaveSpec = SimpleServiceSpec(
        serviceName="redis-slave", 
        containerName="worker", 
        image="kubernetes/redis-slave:v2", 
        containerPort=6379, 
        replicas=2)
  
  val frontEndSpec=SimpleServiceSpec(
        serviceName="frontend", 
        containerName="php-redis",
        image="kubernetes/example-guestbook-php-redis:v2", 
        containerPort=80, 
        replicas=3, 
        serviceType=NodePort, 
        nodePort=30291)
        
  // Build Kubernetes resources locally from the specs    
  val redisMasterResources = build(redisMasterSpec)
  val redisSlaveResources = build(redisSlaveSpec)         
  val frontEndResources = build(frontEndSpec)
  
  // Initialise the skuber client
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val k8s = k8sInit 
  
  // STEP 1
  // Housekeep any Guestbook resources on the cluster that might have been created by a previous
  // run.
  // If any of the resources are not found when housekeeping that is ok - just carry on.
  // The housekeeping is implemented by first scaling down the replica counts to zero, and
  // once the replicas have all been brought down then deleting the controllers and services
  
  val scaleDown = for {
    _    <- scale(redisMasterResources.rc.name, 0, true)
    _    <- scale(redisSlaveResources.rc.name, 0, true)
		last <- scale(frontEndResources.rc.name, 0, true)
  } yield last

  val housekeep = for {
    _ <- scaleDown 
    _ <- okIfNotFound(k8s delete[Service] frontEndResources.service.name)
    _ <- okIfNotFound(k8s delete[ReplicationController] frontEndResources.rc.name)
    _ <- okIfNotFound(k8s delete[Service] redisSlaveResources.service.name)
    _ <- okIfNotFound(k8s delete[ReplicationController] redisSlaveResources.rc.name)
    _ <- okIfNotFound(k8s delete[Service] redisMasterResources.service.name)
    c <- okIfNotFound(k8s delete[ReplicationController] redisMasterResources.rc.name)
  } yield c
  
  // Step 2
  // (re)create the resources once housekeeping done
  
  val createControllers = for {
    _ <- housekeep // ensure any housekeeping calls return first
    controllers <- Future.sequence(List(
                    k8s create redisMasterResources.rc,
                    k8s create redisSlaveResources.rc,
                    k8s create frontEndResources.rc
                   ))
  } yield controllers
  
  val createServices = for {
    _ <- createControllers // ensures the controllers have been created prior to the services
    rm <- k8s create redisMasterResources.service
    rs <- k8s create redisSlaveResources.service
    fe <- k8s create frontEndResources.service
  } yield List(rm,rs,fe)
    
  // monitor created controllers until all specified replicas are running
  val allReplicasRunning = createControllers flatMap {
    controllers => {
      val redisMasterRC = controllers(0)
      val redisSlaveRC = controllers(1)
      val frontEndRC = controllers(2)
      Future.sequence(List(
                         onReplicaCountReached(redisMasterSpec.replicas)(redisMasterRC),
                         onReplicaCountReached(redisSlaveSpec.replicas)(redisSlaveRC),
                         onReplicaCountReached(frontEndSpec.replicas)(frontEndRC)
                       ))
    }
  }
  
  // When all services have been created *and* all replicas are running then the application is deemed to be running
  val guestbookRunning = createServices.zip(allReplicasRunning)
    
  guestbookRunning onSuccess { case (services, controllers) =>
     System.out.println("Successfully completed deployment of:")
     services foreach { service =>
       System.out.println("..service '" + service.name + "' (version=" + service.metadata.resourceVersion + ")")
     }
     controllers foreach { rc => 
       System.out.println("..replication Controller '" + rc.name + "' (version=" + rc.metadata.resourceVersion + 
                                   ", running replicas=" + (rc.status.map {_.replicas.toString } getOrElse("?")) +")" )
     }
  }
  
  guestbookRunning onFailure { 
    case ex: K8SException => {
      System.err.println("Guestbook deployment failed: ")
      System.err.println("..reason : " + ex.status.reason.getOrElse(""))
      System.err.println("..message: " + ex.status.message.getOrElse(""))
      System.err.println("..details: " + ex.status.details.getOrElse(""))
      System.err.println("..status : " + ex.status.status.getOrElse(""))
      System.err.println("..code   : " + ex.status.code.getOrElse(""))
    }
  }
  
  /*
   * Recovers a 404 error result from Kubernetes by basically ignoring the error.
   * Used by housekeeping calls 
   * 
   */
  private def okIfNotFound[T](result: Future[T]) = result recover {
    case ex: K8SException if (ex.status.code.getOrElse(0)==404) => {
      System.out.println("Guestbook: Resource to housekeep not found on server: OK - continuing")
    }
  } 
 
  /*
   * Scale the number of replicas for a given replication controller (RC) to
   * the specified replica count, returning a Future that will complete with
   * the updated RC once  the scaling has been completed on the cluster.
   * The steps to do this are:
   * - get the current RC resource from Kubernetes
   * - modify the replication controller locally to set the desired replica count 
   * - put the updated replication contoller back to Kubernetes. This will start the scaling process.
   * - watch events on the controller until its status shows the desired replica count has been reached
   * The returned value will be a future completed by the last step
   */
  import play.api.libs.iteratee.{Enumerator, Enumeratee}
  
  def scale(name: String, newReplicaCount: Int, ignoreNotFound : Boolean = false) : Future[Any]=  {
    
    System.out.println("Guestbook: Scaling replication controller '" + name + "' to " + newReplicaCount + " replicas") 
    val scaleRC = for {
       currentRC <- k8s get[ReplicationController] name
       respeccedRC = currentRC withReplicas newReplicaCount 
       updatedRC <- k8s update[ReplicationController] respeccedRC
       scalingCompletedRC <- onReplicaCountReached(newReplicaCount)(updatedRC)
       val ignore = System.out.println("Guestbook: Scaling complete for '" + name + "'")
    } yield scalingCompletedRC
   
    scaleRC recover {
      case ex: K8SException if (ex.status.code.getOrElse(0)==404 && ignoreNotFound) => {
        System.out.println("Guestbook: Unable to scale replication controller '" + name + "' as it doesn't exist - OK, continuing")
      }
    }
  }
  
  /*
   * Watch updates to a controller until desired replica count is reached
   * @returns the updated controller after it has reached the count
   */
  import play.api.libs.iteratee.Iteratee
  def onReplicaCountReached(desiredReplicaCount: Int)(rc: ReplicationController) : Future[ReplicationController] = {
 
    if (hasReplicaCount(desiredReplicaCount)(rc)) {
      System.out.println("Guestbook: Replication controller '" + rc.name + "' already has desired replica count " + desiredReplicaCount)
      Future(rc)
    }
    else {
      System.out.println("Guestbook: Replication controller '" + rc.name + "' not yet at desired replica count of " + desiredReplicaCount + ", watching for updates")
      val enumerateControllerEvents = k8s watch rc
      
      val takeOnlyEventsWhereCountIsReached = Enumeratee.filter[ControllerEvent](_._object.status.get.replicas==desiredReplicaCount)    
      val takeFirstEventWhereCountIsReached = takeOnlyEventsWhereCountIsReached compose Enumeratee.take[ControllerEvent](1) 
      
      val readControllerFromFirstEvent = Iteratee.head[ControllerEvent].map { event =>
        val result = event.get._object
        System.out.println("Guestbook: Controller '" + result.name + "' has reached desired replica count") 
        result
      }
      
      val resultEnumerator = enumerateControllerEvents &> takeFirstEventWhereCountIsReached |>> readControllerFromFirstEvent 
      resultEnumerator flatMap (_.run)
    }
  }
  
  /*
   * Return true if the specified number of replicas are running, otherwise false
   */
  def hasReplicaCount(replicaCount: Int)(rc: ReplicationController) = 
    rc.status.map { status => status.replicas == replicaCount } getOrElse(false)  
  
  // build out k8s controller and service resources from a simple spec using a standard pattern which mainly follows
  // the standard guestbook example
  def build(spec: SimpleServiceSpec) : SimpleServiceResources = {
    val nameLabel = "name" -> spec.serviceName
    
    val coreLabels=Map(nameLabel)
    val labels = coreLabels ++ spec.customLabels
    val selector=coreLabels ++ spec.customSelectors
    
    val container=Container(
          name=spec.containerName, 
          image=spec.image, 
          ports=List(Container.Port(spec.containerPort)))
          
    val template = Pod.Template.Spec.withName(spec.serviceName).
                                addLabels(labels).
                                addAnnotations(spec.customAnnotations).                               
                                addContainer(container)
                                
    val controller = ReplicationController(spec.serviceName).
                                addLabels(labels).
                                addAnnotations(spec.customAnnotations).
                                withReplicas(spec.replicas).
                                withSelector(selector).
                                withTemplate(template)
                                
    val servicePort = Service.Port(port=spec.containerPort, targetPort=Some(spec.containerPort))                          
    val service = Service(metadata=ObjectMeta(name=spec.serviceName, labels=labels), 
                          spec=Some(Service.Spec(ports=List(servicePort)).withSelector(selector)))
    return SimpleServiceResources(controller, service)
  }
  
  /**
   * Represents simplified spec for a service from which k8s resources for it can be constructed
   */
  case class SimpleServiceSpec(
      serviceName: String, 
      containerName: String,
      image: String,
      containerPort: Int,
      replicas: Int,
      serviceType: ServiceType = ClusterIP, 
      nodePort: Int = 0, // nodePort is only relevant if serviceType == NodePort
      customSelectors: Map[String, String] = Map(), // additional labels that should be selectors
      customLabels: Map[String, String] = Map(), // additional labels (but not selectors) 
      customAnnotations: Map[String, String] = Map() 
  )
  
  case class SimpleServiceResources(
      rc: ReplicationController,
      service: Service
  )
}