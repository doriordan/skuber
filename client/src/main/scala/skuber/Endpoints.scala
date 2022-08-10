package skuber

/**
 * @author David O'Riordan
 */
case class Endpoints(kind: String = "Endpoints",
                     override val apiVersion: String = v1,
                     metadata: ObjectMeta,
                     subsets: List[Endpoints.Subset] = Nil)
  extends ObjectResource {
  // unlikely any skuber clients will construct their own endpoints, but if so can use these fluent methods

  def withEndpoint(ip: String, port: Int, protocol: Protocol.Value = Protocol.TCP): Endpoints =
    this.copy(subsets = Endpoints.Subset(Endpoints.Address(ip) :: Nil,
      None,
      Endpoints.Port(port, protocol) :: Nil)
      :: Nil)

  def addEndpoints(subset: Endpoints.Subset): Endpoints = this.copy(subsets = subset :: subsets)
}

object Endpoints {

  val specification: CoreResourceSpecification = CoreResourceSpecification(scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(plural = "endpoints",
      singular = "endpoints",
      kind = "Endpoints",
      shortNames = Nil))
  implicit val epsDef: ResourceDefinition[Endpoints] = new ResourceDefinition[Endpoints] {
    def spec: ResourceSpecification = specification
  }
  implicit val epsListDef: ResourceDefinition[EndpointsList] = new ResourceDefinition[EndpointsList] {
    def spec: ResourceSpecification = specification
  }

  case class Subset(addresses: List[Address],
                     notReadyAddresses: Option[List[Address]],
                     ports: List[Port])

  case class Address(ip: String, targetRef: Option[ObjectReference] = None) {
    def references(obj: ObjectResource): Address = this.copy(ip, targetRef = Some(obj))
  }

  case class Port(port: Int, protocol: Protocol.Value = Protocol.TCP, name: Option[String] = None)
}
