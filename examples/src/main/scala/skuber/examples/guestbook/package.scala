package skuber.examples.guestbook

import skuber._

package object model {

  case class GuestbookServiceSpecification(serviceName: String, 
      containerName: String,
      image: String,
      containerPort: Int,
      replicas: Int,
      serviceType: Service.Type.ServiceType = Service.Type.ClusterIP,
      nodePort: Int = 0, // nodePort is only relevant if serviceType == NodePort
      customSelectors: Map[String, String] = Map(), // additional labels that should be selectors
      customLabels: Map[String, String] = Map(), // additional labels (but not selectors) 
      customAnnotations: Map[String, String] = Map() ) 
  {      
      
      def buildKubernetesResources =  {
         // build out k8s controller and service resources from this spec using a standard pattern
  
        val nameLabel = "name" -> serviceName
    
        val coreLabels=Map(nameLabel)
        val labels = coreLabels ++ customLabels
        val selector=coreLabels ++ customSelectors
    
        val container=Container(containerName, image=image).exposePort(containerPort)
          
        val template = Pod.Template.Spec.named(serviceName).
                             addLabels(labels).
                             addAnnotations(customAnnotations).                               
                             addContainer(container)
                                
        val controller = ReplicationController(serviceName).
                             addLabels(labels).
                             addAnnotations(customAnnotations).
                             withReplicas(replicas).
                             withSelector(selector).
                             withTemplate(template)
                                
        val servicePort = Service.Port(port=containerPort, nodePort = nodePort)                          
        val service = Service(metadata=ObjectMeta(name=serviceName, labels=labels), 
                              spec=Some(Service.Spec(ports=List(servicePort),_type=serviceType).withSelector(selector)))
    
        GuestbookServiceResources(controller, service)
      }
  }
  
  case class GuestbookServiceResources(rc: ReplicationController,
      service: Service)
}