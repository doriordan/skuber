package skuber

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Endpoints(
  	val kind: String ="Endpoints",
  	override val apiVersion: String = v1,
    val metadata: ObjectMeta,
    subsets: List[Endpoints.Subset] = Nil)       
  extends ObjectResource 
{
    // unlikely any skuber clients will construct their own endpoints, but if so can use these fluent methods

    def withEndpoint(ip: String, port: Int, protocol: Protocol.Value = Protocol.TCP) =
          this.copy(subsets=Endpoints.Subset(
                                    Endpoints.Address(ip)::Nil,
                                    Nil,
                                    Endpoints.Port(port,protocol)::Nil
                                  )
                                  ::Nil
                   )
                   
    def addEndpoints(subset: Endpoints.Subset) = this.copy(subsets = subset::subsets)
}

object Endpoints {

  val specification=CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(
      plural = "endpoints",
      singular = "endpoints",
      kind = "Endpoints",
      shortNames = Nil
    )
  )
  implicit val epsDef = new ResourceDefinition[Endpoints] { def spec=specification }
  implicit val epsListDef = new ResourceDefinition[EndpointsList] { def spec=specification }

  case class Subset(
    addresses: List[Address],
    notReadyAddresses: List[Address],
    ports: List[Port])
      
  case class Address(ip: String, targetRef: Option[ObjectReference] = None) {
    def references(obj: ObjectResource) = this.copy(ip, targetRef = Some(obj))
  }

  case class Port(port: Int, protocol: Protocol.Value  = Protocol.TCP, name: String = "")
}
