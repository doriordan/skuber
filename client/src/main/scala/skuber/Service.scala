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

  import Endpoints._
  def mapsToEndpoint(ip: String, port: Int, protocol : Protocol.Value = Protocol.TCP): Endpoints =
      Endpoints(metadata=ObjectMeta(name=this.name, namespace=this.ns),subsets=Subset(Address(ip)::Nil, Port(port,protocol)::Nil)::Nil)
  def mapsToEndpoints(subset: Subset): Endpoints =
      Endpoints(metadata=ObjectMeta(name=this.name, namespace=this.ns),subsets=subset::Nil)
  def mapsToEndpoints(subsets: List[Subset]): Endpoints = Endpoints(metadata=ObjectMeta(name=this.name, namespace=this.ns),subsets = subsets)
}

object Service {
  
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
    val ClusterIP, NodePort,LoadBalancer = Value
   }
   
   case class Port(
       name: String = "",
       protocol: Protocol.Value = Protocol.TCP,
       port: Int,
       targetPort: Option[NameablePort]=None,
       nodePort: Int = 0)
       
   import Type._   
   case class Spec( 
      ports: List[Port],
      selector: Map[String,String]=Map(),
      clusterIP: String = "", // empty by default - can also be "None" or an IP Address
      _type: ServiceType=ClusterIP,
      externalIPs: List[String] = List(),
      sessionAffinity: Affinity.Affinity = Affinity.None)    {
     def withSelector(sel: Map[String, String]) : Spec = this.copy(selector = sel)
     def withSelector(sel: Tuple2[String,String]): Spec = withSelector(Map(sel))
   }      
   
  case class Status(loadBalancer: Option[LoadBalancer.Status] = None)     
  
  object LoadBalancer {
     case class Status(ingress: List[Ingress] = Nil)
     case class Ingress(ip: Option[String] = None, hostName: Option[String]=None)
   }
}