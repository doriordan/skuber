package skuber

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Service(
  	val kind: String ="Service",
  	override val apiVersion: String = v1,
    val metadata: ObjectMeta,
    spec: Option[Service.Spec] = None,
    status: Option[Service.Status] = None) 
      extends ObjectResource {

  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

  lazy val copySpec = this.spec.getOrElse(new Service.Spec)
  
  def addLabel(lbl: Tuple2[String,String]) = this.copy(metadata = metadata.copy(labels = metadata.labels + lbl))
  def addLabels(lbls: Map[String,String]) = this.copy(metadata = metadata.copy(labels = metadata.labels ++ lbls))    
   
  def withSelector(sel: Map[String, String])  = this.copy(spec = Some(copySpec.copy(selector = sel)))
  def withSelector(sel: Tuple2[String,String])= this.copy(spec = Some(copySpec.copy(selector = Map(sel))))
  
  def setPort(port: Service.Port) = this.copy(spec = Some(copySpec.copy(ports = List(port))))
  def setPorts(ports: List[Service.Port]) = this.copy(spec = Some(copySpec.copy(ports = ports)))
  def exposeOnPort(port: Service.Port) = this.copy(spec = Some(copySpec.copy(ports = port :: copySpec.ports )))
  
  def exposeOnNodePort(bind: Tuple2[Int,Int], name: String="") = {
    val nodePort = Service.Port(name=name, port=bind._2, nodePort=bind._1)
    val updated = exposeOnPort(nodePort)
    // set type to node port - only if set to ClusterIP / default
    val currType = updated.spec.get._type
    if (currType==Service.Type.ClusterIP)
      updated.withType(Service.Type.NodePort)
    else
      updated
  }
    
  def withClusterIP(ip: String) = this.copy(spec = Some(copySpec.copy(clusterIP = ip)))
  def withType(_type: Service.Type.Value) = this.copy(spec = Some(copySpec.copy(_type = _type)))
  def withLoadBalancerType = withType(Service.Type.LoadBalancer)
  
  def withExternalIP(ip: String) = this.copy(spec = Some(copySpec.copy(externalIPs = List(ip))))
  def withExternalIPs(ips: List[String]) = this.copy(spec = Some(copySpec.copy(externalIPs = ips)))
  def addExternalIP(ip: String) = this.copy(spec = Some(copySpec.copy(externalIPs = ip :: copySpec.externalIPs)))
      
  def withSessionAffinity(affinity: Service.Affinity.Value) = this.copy(spec = Some(copySpec.copy(sessionAffinity = affinity)))
      
  import Endpoints._
  def mapsToEndpoint(ip: String, port: Int, protocol : Protocol.Value = Protocol.TCP): Endpoints =
      Endpoints(metadata=ObjectMeta(name=this.name, namespace=this.ns),subsets=Subset(Address(ip)::Nil, Nil, Port(port,protocol)::Nil)::Nil)
  def mapsToEndpoints(subset: Subset): Endpoints =
      Endpoints(metadata=ObjectMeta(name=this.name, namespace=this.ns),subsets=subset::Nil)
  def mapsToEndpoints(subsets: List[Subset]): Endpoints = Endpoints(metadata=ObjectMeta(name=this.name, namespace=this.ns),subsets = subsets)
}

object Service {

  val specification=CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(
      plural = "services",
      singular = "service",
      kind = "Service",
      shortNames = List("svc")
    )
  )
  implicit val svcDef = new ResourceDefinition[Service] { def spec=specification }
  implicit val svcListDef = new ResourceDefinition[ServiceList] { def spec=specification }

  def apply(name: String): Service = Service(metadata=ObjectMeta(name=name))
  def apply(name: String, spec: Service.Spec) : Service =
    Service(metadata=ObjectMeta(name=name), spec = Some(spec))
  def apply(name: String, selector: Map[String, String], port: Int): Service =
    apply(name, selector, Port(port=port))
  def apply(name: String, selector: Map[String,String], port: Port): Service = {
    val meta=ObjectMeta(name=name)
    val serviceType=if (port.nodePort != 0) Type.NodePort else Type.ClusterIP
    val spec=Spec(ports=List(port), selector=selector,_type=serviceType)
    Service(metadata=meta,spec=Some(spec))
  }
   
  object Affinity extends Enumeration {
    type Affinity=Value
    val ClientIP, None=Value
  }
   
  object Type extends Enumeration {
    type ServiceType = Value
    val ClusterIP, NodePort, LoadBalancer, ExternalName = Value
  }
   
  case class Port(
    name: String = "",
    protocol: Protocol.Value = Protocol.TCP,
    port: Int,
    targetPort: Option[NameablePort]=None,
    nodePort: Int = 0)
       
   import Type._   
   case class Spec( 
     ports: List[Port] = List(),
     selector: Map[String,String]=Map(),
     clusterIP: String = "", // empty by default - can also be "None" or an IP Address
     _type: ServiceType=ClusterIP,
     externalIPs: List[String] = List(),
     externalName: String = "",
     sessionAffinity: Affinity.Affinity = Affinity.None)
   {
     def withSelector(sel: Map[String, String]) : Spec = this.copy(selector = sel)
     def withSelector(sel: Tuple2[String,String]): Spec = withSelector(Map(sel))
   }      
   
  case class Status(loadBalancer: Option[LoadBalancer.Status] = None)     
  
  object LoadBalancer {
    case class Status(ingress: List[Ingress] = Nil)
    case class Ingress(ip: Option[String] = None, hostName: Option[String]=None)
  }
}
