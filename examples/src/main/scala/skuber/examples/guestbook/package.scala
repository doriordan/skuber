package skuber.examples.guestbook

package object model {

  import skuber.{ObjectMeta, portNumToNameablePort, ReplicationController,Container,Pod,Service}
  import skuber.Service.Type.{ServiceType,ClusterIP}

  case class GuestbookServiceSpecification(
      serviceName: String, 
      containerName: String,
      image: String,
      containerPort: Int,
      replicas: Int,
      serviceType: ServiceType = ClusterIP, 
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
    
        val container=Container(
          name=containerName, 
          image=image, 
          ports=List(Container.Port(containerPort)))
          
        val template = Pod.Template.Spec.withName(serviceName).
                             addLabels(labels).
                             addAnnotations(customAnnotations).                               
                             addContainer(container)
                                
        val controller = ReplicationController(serviceName).
                             addLabels(labels).
                             addAnnotations(customAnnotations).
                             withReplicas(replicas).
                             withSelector(selector).
                             withTemplate(template)
                                
        val servicePort = Service.Port(port=containerPort, targetPort=Some(containerPort))                          
        val service = Service(metadata=ObjectMeta(name=serviceName, labels=labels), 
                              spec=Some(Service.Spec(ports=List(servicePort)).withSelector(selector)))
    
        GuestbookServiceResources(controller, service)
      }
  }
  
  case class GuestbookServiceResources(
      rc: ReplicationController,
      service: Service
  )
}