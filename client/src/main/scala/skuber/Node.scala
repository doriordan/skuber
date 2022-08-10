package skuber


import java.util.Date

/**
 * @author David O'Riordan
 */
case class Node(val kind: String = "Node",
                override val apiVersion: String = v1,
                val metadata: ObjectMeta,
                spec: Option[Node.Spec] = None,
                status: Option[Node.Status] = None)
  extends ObjectResource {

  def withResourceVersion(version: String): Node = this.copy(metadata = metadata.copy(resourceVersion = version))

}

object Node {

  val specification: CoreResourceSpecification = CoreResourceSpecification(scope = ResourceSpecification.Scope.Cluster,
    names = ResourceSpecification.Names(plural = "nodes",
      singular = "node",
      kind = "Node",
      shortNames = List("no")))
  implicit val nodeDef: ResourceDefinition[Node] = new ResourceDefinition[Node] {
    def spec: ResourceSpecification = specification
  }
  implicit val nodeListDef: ResourceDefinition[NodeList] = new ResourceDefinition[NodeList] {
    def spec: ResourceSpecification = specification
  }

  def named(name: String): Node = Node(metadata = ObjectMeta(name = name))

  def apply(name: String, spec: Node.Spec): Node = Node(metadata = ObjectMeta(name = name), spec = Some(spec))

  case class Spec(podCIDR: String = "",
                   providerID: String = "",
                   unschedulable: Boolean = false,
                   externalID: String = "",
                   taints: List[Taint] = Nil)

  case class Taint(effect: String,
                    key: String,
                    value: Option[String] = None,
                    timeAdded: Option[Timestamp] = None)

  case class Status(capacity: Resource.ResourceList = Map(),
                     phase: Option[Phase.Phase] = None,
                     conditions: List[Node.Condition] = List(),
                     addresses: List[Node.Address] = List(),
                     nodeInfo: Option[Node.SystemInfo] = None,
                     allocatable: Resource.ResourceList = Map(),
                     daemonEndpoints: Option[DaemonEndpoints] = None,
                     images: List[Container.Image] = Nil,
                     volumesInUse: List[String] = Nil,
                     volumesAttached: List[AttachedVolume])

  case class DaemonEndpoints(kubeletEndpoint: DaemonEndpoint)

  case class DaemonEndpoint(Port: Int)

  case class AttachedVolume(name: String, devicePath: String)

  object Phase extends Enumeration {
    type Phase = Value
    val Pending, Running, Terminated = Value
  }

  case class Condition(_type: String,
                        status: String,
                        lastHeartbeatTime: Option[Timestamp] = None,
                        lastTransitionTime: Option[Timestamp] = None,
                        reason: Option[String] = None,
                        message: Option[String] = None)

  case class Address(_type: String, address: String)

  case class SystemInfo(machineID: String,
                         systemUUID: String,
                         bootID: String,
                         kernelVersion: String,
                         osImage: String,
                         containerRuntimeVersion: String,
                         kubeletVersion: String,
                         kubeProxyVersion: String)
}