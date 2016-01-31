package skuber.examples.fluent

import skuber._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import skuber.json.format._
  
/**
 * @author David O'Riordan
 * 
 * Some examples of using the fluent interface methods f the Skuber API to build out specs for
 * various Kubernetes resources.
 * After running the example you can run 'kubect get rc' and 'kubectl get services' to 
 * verify the requested resources have all been created.
 * 
 */
object FluentExamples extends App {
  
  val image = "nginx"
  val namePrefix = "nginx"
    
  val env  = "env"
  val zone = "zone"
    
  val devLabel  = env -> "dev"   
  val testLabel = env -> "test"
  val prodLabel = env -> "production"
    
  val testInternalZoneLabel = zone -> "test-internal"
  val testExternalZoneLabel = zone -> "test-external"
    
  val prodInternalZoneLabel = zone -> "prod-internal"
  val prodExternalZoneLabel = zone -> "prod-external"  
  
  val testInternalSelector = Map(testLabel, testInternalZoneLabel)
  val testExternalSelector = Map(testLabel, testExternalZoneLabel)
  val prodInternalSelector = Map(prodLabel, prodInternalZoneLabel)
  val prodExternalSelector = Map(prodLabel, prodExternalZoneLabel)
  
  val k8s = k8sInit
  
  val depl = deployNginxServices
  
  /*
   * Helper to get a service of a given name either from the existing resource (if it exists on the cluster)
   * or newly initialized
   */
  private def getService(currentList: Future[ServiceList], svcName: String) : Future[Service] = {
    
     val existingSvc = currentList map {
            _.filter(_.name==svcName)
            .headOption
            .map { _ => k8s get[Service] svcName }
     }
     
     existingSvc flatMap { 
       _ match {
            case Some(currentService) => currentService
            case None                 => Future { Service(svcName) }
       }
     }
  }
  
  /*
   * Helper to get a replication controller of a given name either from the existing resource (if it exists on the cluster)
   * or newly initialized
   */
  private def getController(currentList: Future[ReplicationControllerList], rcName: String) : Future[ReplicationController] = {
    
     val existingRC = currentList map { 
         _.filter(_.name==rcName)
         .headOption
         .map { _ => k8s get[ReplicationController] rcName }
     }
          
     existingRC flatMap { 
       _ match {
          case Some(currentRC) => currentRC
          case None            => Future { ReplicationController(rcName) }
       }
     }
  }
  
  /*
   * Build a simple nginx service, which can be accessed via port 30001 on any of the cluster nodes,
   * and is required to have 5 replicas running.
   */
  def buildSimpleNginxService: (Service, ReplicationController) = {
    
    val nginxSelector  = Map("app" -> "nginx")
    val nginxContainer = Container("nginx",image="nginx").port(80)
    val nginxController= ReplicationController("nginx",nginxContainer,nginxSelector).withReplicas(5)
    val nginxService   = Service("nginx", nginxSelector, Service.Port(port=80, nodePort=30001)) 
    (nginxService,nginxController)
  }
 
    
  /**
   * Build a set of replication controllers for nginx based services that target multiple environments
   * and zones, using labels and selectors to differentiate these different targets.
   * 
   */
  def buildNginxControllers: Future[List[ReplicationController]] = {
    
    // As a prerequisite get a list of current controllers, the applicable ones will be updated if they already exist
    val rcList = k8s list[ReplicationControllerList]()    
     
    // 1. create dev controller

    val devCPU = 0.5 // 0.5. KCU
    val devMem = "256Mi"  // 256 MiB (mebibytes)
    
    val devContainer=Container(name="nginx-dev",image="nginx")
           .limitCPU(devCPU)
           .limitMemory(devMem).port(80)
           
    val devPodSpec = Pod.Spec()
           .addContainer(devContainer)
    
    val devController = 
      getController(rcList, "nginx-dev") map {
        _.addLabel(devLabel)
        .withSelector(Map(devLabel))
        .withPodSpec(devPodSpec)
        .withReplicas(1)
      }
    
    // 2. Create test controllers, one each for internal and external zones
    // A node selector will be specified on each to ensure pods only get assigned to nodes in
    // the right zone
    
    val testCPU = 1 // 1 KCU 
    val testMem = "0.5Gi" // 0.5GiB (gigibytes) 
    
    val testContainer=
      Container(name="nginx-test", image="nginx")
        .limitCPU(testCPU)
        .limitMemory(testMem)
        .port(80)
    
    val internalTestPodSpec=Pod.Spec(containers=List(testContainer), nodeSelector=Map(testInternalZoneLabel))     
    
    val internalTestController =
      getController(rcList, "nginx-test-int") map {
        _.addLabels(testInternalSelector)
         .withSelector(testInternalSelector)
         .withReplicas(2)
         .withPodSpec(internalTestPodSpec)
      }
                          
    val externalTestPodSpec=Pod.Spec(containers=List(testContainer), nodeSelector=Map(testExternalZoneLabel))     
    
    val externalTestController=
      getController(rcList,"nginx-test-ext") map {
        _.addLabels(testExternalSelector)
         .withSelector(testInternalSelector)
         .withReplicas(2)
         .withPodSpec(externalTestPodSpec)
      }
    
    // 3. Create production controllers, one each for internal and external zones
    // A node selector will be specified on each  to ensure pods only get assigned to nodes in
    // the right zone
                                  
    val prodCPU = 1 // 1 KCU 
    val prodMem = "0.5Gi" // 0.5GiB (gigibytes)    
    
    val prodContainer=
      Container(name="nginx-prod", image="nginx")
        .limitCPU(prodCPU)
        .limitMemory(prodMem)
        .port(80)
    
    val internalProdPodSpec=
      Pod.Spec(
        containers=List(prodContainer), 
        nodeSelector=Map(prodInternalZoneLabel))     
                                     
    val internalProdController=
      getController(rcList,"nginx-prod-int") map { 
        _.addLabels(prodInternalSelector)
         .withSelector(prodInternalSelector)
         .withReplicas(8)
         .withPodSpec(internalProdPodSpec)
      }
                          
    val externalProdPodSpec=Pod.Spec(containers=List(prodContainer), nodeSelector=Map(prodExternalZoneLabel))     
    
    val externalProdController=
     getController(rcList, "nginx-prod-ext") map {
       _.addLabels(prodExternalSelector)
       .withSelector(prodExternalSelector)
       .withReplicas(64)
       .withPodSpec(externalProdPodSpec)
     }
                                  
    Future.sequence(List(devController, internalTestController, externalTestController, internalProdController, externalProdController))                                          
  }
  
  /*
   * Build services for each environment and zone, each service uses a selector to target the desired env/zone combination.
   */
  def buildNginxServices: Future[List[Service]] = {
    
    val svcList = k8s list[ServiceList]()
    
    // we make service available outside K8S cluster using node ports, 
    // exposing node ports 30001 to 30005 (depending on env), each routing to pod port 80
    
    val devService = 
      getService(svcList, "nginx-dev") map { 
        _.withSelector(devLabel)
         .withNodePort(30001 -> 80)
      } 
    
    val internalTestService = 
      getService(svcList, "nginx-test-int") map {
        _.withSelector(testInternalSelector)
         .withNodePort(30002 -> 80)
      } 
  
    val externalTestService = 
       getService(svcList, "nginx-test-ext") map {
        _.withSelector(testExternalSelector)
         .withNodePort(30003 -> 80)
      } 

    // for prod we require a load balancer on top of the node port
    
    val internalProdService=
      getService(svcList, "nginx-prod-int") map {
       _.addLabels(Map("app" -> "internal-web-server"))
       .withSelector(prodInternalSelector)
       .withNodePort(30004 -> 80)
       .withLoadBalancerType
    }
            
    val externalProdService=
      getService(svcList, "nginx-prod-ext") map {
        _.addLabels(Map("app" -> "external-web-server"))
        .withSelector(prodExternalSelector)
        .withNodePort(30005 -> 80)
        .withLoadBalancerType
      }
    
    Future.sequence(List(devService, internalTestService, externalTestService,internalProdService,externalProdService))
  }
  
  def deployControllers: Future[List[ReplicationController]] = {
      
    val newControllerBuilds = buildNginxControllers
    val listCurrentControllers = k8s list[ReplicationControllerList]()
      
    listCurrentControllers flatMap { existingRCList =>
        
      println("Deploying controllers...")
    
      newControllerBuilds flatMap { rcDeploymentList =>
          
        def alreadyExists(rc: ReplicationController) = existingRCList.exists { _.name == rc.name }
          
        val updateList = for {
          rcUpdateCandidate <- rcDeploymentList
          if (alreadyExists(rcUpdateCandidate)) 
        } yield rcUpdateCandidate
          
        val createList = for {
          rcCreateCandidate <- rcDeploymentList
          if (!alreadyExists(rcCreateCandidate))
        } yield rcCreateCandidate
          
        val doUpdates = updateList map { rc => k8s update rc }
        val doCreates = createList map { rc => k8s create rc }
     
        Future.sequence(doCreates ++ doUpdates)
      }
    }
  }
   
  def deployServices : Future[List[Service]] = {
      
    val newServiceBuilds = buildNginxServices
    val listCurrentServices = k8s list[ServiceList]()
      
    listCurrentServices flatMap { existingSvcList =>       
        
      println("Deploying services...")
      newServiceBuilds flatMap { svcDeploymentList => 
          
        def alreadyExists(svc: Service) = existingSvcList.exists { _.name == svc.name }
          
        val updateList = for {
          svcUpdateCandidate <- svcDeploymentList
          if (alreadyExists(svcUpdateCandidate)) 
        } yield svcUpdateCandidate
          
        val createList = for {
          svcCreateCandidate <- svcDeploymentList
          if (!alreadyExists(svcCreateCandidate))
        } yield svcCreateCandidate
          
        val doUpdates = updateList map { svc => k8s update svc }
        val doCreates = createList map { svc => k8s create svc }
     
        Future.sequence(doCreates ++ doUpdates)
      }
    }
  }
      
  def deployNginxServices = {
    
   val deployAll = for {
      s <- deployServices
      c <- deployControllers
    } yield c 
    
    deployAll onFailure { case ex: K8SException => System.err.println("Request failed with status : " + ex.status) }
    
    deployAll andThen { case _ => k8s.close }
  }
}