package skuber.model

import Model._

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Service(
  	val kind: String ="Service",
  	override val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    spec: Option[Service.Spec] = None,
    status: Option[Service.Status] = None) 
      extends ObjectResource with KListItem

object Service {
  
   def forName(name: String) = Service(metadata=ObjectMeta(name=name))
   def forNameAndSpec(name: String, spec: Service.Spec) = Service(metadata=ObjectMeta(name=name), spec = Some(spec))
  
   object Affinity extends Enumeration {
     type Affinity=Value
     val ClientIP, None=Value
   }
   
   object Type extends Enumeration {
    type Type = Value
    val ClusterIP,NodePort,LoadBalancer = Value
   }
   
   case class Port(
       name: String = "",
       protocol: Protocol.Value = Protocol.TCP,
       port: Int,
       targetPort: Option[NameablePort]=None,
       nodePort: Int = 0)
       
      
   case class Spec( 
      ports: List[Port],
      selector: Map[String,String]=Map(),
      clusterIP: String = "", // empty by default - can also be "None" or an IP Address
      _type: Type.Type=Type.ClusterIP,
      sessionAffinity: Affinity.Affinity = Affinity.None)          
   
  case class Status(loadBalancer: Option[LoadBalancer.Status] = None)     
  
  object LoadBalancer {
     case class Status(ingress: List[Ingress] = Nil)
     case class Ingress(ip: Option[String] = None, hostName: Option[String]=None)
   }
}