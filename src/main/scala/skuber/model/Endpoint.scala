package skuber.model

import Model._

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Endpoint(
  	val kind: String ="Endpoint",
  	override val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    subsets: List[Endpoint.Subset]) 
      extends ObjectResource with KListItem

object Endpoint {
   case class Subset(
      addresses: List[Address],
      port: List[Port])
      
   case class Address(ip: String, targetReference: Option[ObjectReference] = None)  
   case class Port(port: Int, protocol: Protocol.Value  = Protocol.TCP, name: String = "")   
}