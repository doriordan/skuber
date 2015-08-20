package skuber.model

import Model._

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Endpoints(
  	val kind: String ="Endpoint",
  	override val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    subsets: List[Endpoints.Subset] = Nil)       
  extends ObjectResource with KListItem 
{
    
    def withEndpoint(ip: String, port: Int, protocol: Protocol.Value = Protocol.TCP) = 
          this.copy(subsets=Endpoints.Subset(
                                    Endpoints.Address(ip)::Nil, 
                                    Endpoints.Port(port,protocol)::Nil
                                  )
                                  ::Nil
                   )
                   
    def withEndpoints(subset: Endpoints.Subset) = this.copy(subsets = subset::subsets)      
}

object Endpoints {    
   case class Subset(
      addresses: List[Address],
      ports: List[Port])
      
   case class Address(ip: String, targetRef: Option[ObjectReference] = None) {
       def references(obj: ObjectResource) = this.copy(ip, targetRef = Some(obj))
   }
   case class Port(port: Int, protocol: Protocol.Value  = Protocol.TCP, name: String = "")   
}