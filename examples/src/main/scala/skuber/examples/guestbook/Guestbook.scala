package skuber.examples.guestbook

import skuber.api.client._
import skuber.model._
import skuber.model.coretypes._

/**
 * @author David O'Riordan
 */
object Guestbook extends App {
 
  import Service.Type._ 
  
  val redisMasterManifest = GuestbookComponentManifest(
        serviceName="redis-master",
        containerName="master",
        image="redis", 
        containerPort=6379, 
        replicas=1)
  
  val redisSlaveManifest = GuestbookComponentManifest(
        serviceName="redis-slave", 
        containerName="worker", 
        image="kubernetes/redis-slave:v2", 
        containerPort=6379, 
        replicas=2)
  
  val frontEndManifest=GuestbookComponentManifest(
        serviceName="frontend", 
        containerName="php-redis",
        image="kubernetes/example-guestbook-php-redis:v2", 
        containerPort=80, 
        replicas=3, 
        serviceType=NodePort, 
        nodePort=30291)
        
  // Build Kubernetes specifications from the simple component manifests above      
  val redisMasterSpec = buildKubernetesSpecification(redisMasterManifest)
  val redisSlaveSpec = buildKubernetesSpecification(redisSlaveManifest)         
  val frontEndSpec = buildKubernetesSpecification(frontEndManifest)
 
  case class GuestbookComponentManifest(
      serviceName: String,
      containerName: String,
      image: String,
      containerPort: Int,
      replicas: Int,
      serviceType: ServiceType = ClusterIP, 
      nodePort: Int = 0 // nodePort is only relevant if serviceType == NodePort    
  )
  
  case class GuestbookComponentSpec(
      rc: ReplicationController,
      service: Service
  )
      
  // build out k8s controller and service specifications from a "manifest"
  private def buildKubernetesSpecification(mfst: GuestbookComponentManifest) : GuestbookComponentSpec = {
    val nameLabel = "name" -> mfst.serviceName
    val container=Container(
          name=mfst.containerName, 
          image=mfst.image, 
          ports=List(Container.Port(mfst.containerPort)))
    val template = Pod.Template.Spec.withName(mfst.serviceName).
                                addLabel(nameLabel).
                                addContainer(container)
    val controller = ReplicationController(mfst.serviceName).
                                addLabel(nameLabel).
                                withReplicas(mfst.replicas).
                                withSelector(nameLabel).
                                withTemplate(template)
                                
    val servicePort = Service.Port(port=mfst.containerPort, targetPort=Some(mfst.containerPort))                          
    val service = Service(mfst.serviceName, 
                          Service.Spec(ports=List(servicePort)).withSelector(nameLabel))
    return GuestbookComponentSpec(controller, service)
  }
}