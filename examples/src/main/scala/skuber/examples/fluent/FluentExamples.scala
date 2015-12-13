package skuber.examples.fluent

import skuber._

/**
 * @author David O'Riordan
 */
object FluentExamples {
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
  def buildNginxControllers: List[ReplicationController] = {
    
    // 1. create dev controller

    val devCPU = 0.5 // 0.5. KCU
    val devMem = "256Mi"  // 256 MiB (mebibytes)
    
    val devContainer=Container(name="nginx-dev",image="nginx").limitCPU(devCPU).limitMemory(devMem).port(80)
    val devController = ReplicationController("nginx-dev", devContainer, Map(devLabel)).withReplicas(1)
    
    // 2. Create test controllers, one each for internal and external zones
    // A node selector will be specified on each to ensure pods only get assigned to nodes in
    // the right zone
    
    val testCPU = 1 // 1 KCU 
    val testMem = "0.5Gi" // 0.5GiB (gigibytes)    
    val testContainer=Container(name="nginx-test", image="nginx").limitCPU(testCPU).limitMemory(testMem).port(80)
    
    val internalTestPodSpec=Pod.Spec(containers=List(testContainer), nodeSelector=Map(testInternalZoneLabel))     
    val internalTestController=ReplicationController("nginx-test-int").
                                  addLabels(testInternalSelector).
                                  withSelector(testInternalSelector).
                                  withReplicas(2).
                                  withPodSpec(internalTestPodSpec)
                          
    val externalTestPodSpec=Pod.Spec(containers=List(testContainer), nodeSelector=Map(testExternalZoneLabel))     
    val externalTestController=ReplicationController("nginx-test-ext").
                                  addLabels(testExternalSelector).
                                  withSelector(testInternalSelector).
                                  withReplicas(2).
                                  withPodSpec(externalTestPodSpec)
                     
    // 3. Create production controllers, one each for internal and external zones
    // A node selector will be specified on each  to ensure pods only get assigned to nodes in
    // the right zone
    val prodCPU = 1 // 1 KCU 
    val prodMem = "0.5Gi" // 0.5GiB (gigibytes)    
    val prodContainer=Container(name="nginx-prod", image="nginx").limitCPU(prodCPU).limitMemory(prodMem).port(80)
    
    val internalProdPodSpec=Pod.Spec(containers=List(prodContainer), nodeSelector=Map(prodInternalZoneLabel))     
    val internalProdController=ReplicationController("nginx-prod-int").
                                  addLabels(prodInternalSelector).
                                  withSelector(prodInternalSelector).
                                  withReplicas(8).
                                  withPodSpec(internalProdPodSpec)
                          
    val externalProdPodSpec=Pod.Spec(containers=List(prodContainer), nodeSelector=Map(prodExternalZoneLabel))     
    val externalProdController=ReplicationController("nginx-prod-ext").
                                  addLabels(prodExternalSelector).
                                  withSelector(prodExternalSelector).
                                  withReplicas(64).
                                  withPodSpec(externalProdPodSpec)
                                  
    List(devController, internalTestController, externalTestController, internalProdController, externalProdController)                                          
  }
  
  /*
   * Build services for each environment and zone, each service uses a selector to target the desired env/zone combination.
   */
  val buildNginxServices: List[Service] = {
    
    // for dev and test we make service available outside K8S cluster using node ports
    val devService = Service(name="nginx-dev" , selector = Map(devLabel), Service.Port(port=80, nodePort=30001))         
    val internalTestService = Service(name="nginx-test-int", selector = testInternalSelector,  Service.Port(port=80, nodePort=30002))
    val externalTestService = Service(name="nginx-test-ext", selector = testExternalSelector,  Service.Port(port=80, nodePort=30003))

    // for prod we leverage a load balancer instead
    val prodServiceSpec=Service.Spec(ports=List(Service.Port(port=80)), _type=Service.Type.LoadBalancer)    
    val internalProdService = Service(name="nginx-prod-int", prodServiceSpec)   
    val externalProdService = Service(name="nginx-prod-ext", prodServiceSpec)
    
    List(devService, internalTestService, externalTestService,internalProdService,externalProdService)
  }
  
  
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future
  import scala.util.Success
  import skuber.json.format._
    
  val deployNginxServices : Future[Any] = {
    
    val controllers=buildNginxControllers
    val services=buildNginxServices
    
    val k8s = k8sInit
    
    def ignoreIfNotThere[O <: ObjectResource](create: Future[O])  = create recover {
      case ex: K8SException if (ex.status.code.contains(404)) =>      
    }
    
    def createControllers = Future.sequence { controllers map { c => ignoreIfNotThere(k8s create c) } }
    def createServices    = Future.sequence { services    map { s => ignoreIfNotThere(k8s create s) } }
    
    for {
      c <- createControllers
      s <- createServices 
    } yield s 
  }
}