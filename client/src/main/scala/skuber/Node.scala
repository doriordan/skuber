package skuber


import java.util.Date

/**
 * @author David O'Riordan
 */
case class Node(
  	val kind: String ="Node",
  	override val apiVersion: String = v1,
    val metadata: ObjectMeta,
    spec: Option[Node.Spec] = None,
    status: Option[Node.Status] = None) 
      extends ObjectResource {
  
  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

}

object Node {
   def named(name: String) = Node(metadata=ObjectMeta(name=name))
   def apply(name: String, spec: Node.Spec) : Node = Node(metadata=ObjectMeta(name=name), spec = Some(spec))
  
   case class Spec(
      podCIDR: String = "",
      providerID: String = "",
      unschedulable: Boolean = false,
      externalID: String = "")
      
  case class Status(
      capacity: Resource.ResourceList=Map(),
      phase: Option[Phase.Phase] = None,
      conditions: List[Node.Condition] = List(),
      addresses: List[Node.Address] = List(),
      nodeInfo: Option[Node.SystemInfo] = None)
      
  object Phase extends Enumeration {
     type Phase = Value
     val Pending, Running,Terminated = Value
   }
  
  case class Condition(
      _type : String, 
      status: String, 
      lastHeartbeatTime: Option[Timestamp]=None,
      lastTransitionTime: Option[Timestamp] = None,
      reason: Option[String] = None,
      message: Option[String] = None)
 
  case class Address(_type: String, address: String)

  case class SystemInfo(
      machineID: String,
      systemUUID: String,
      bootID: String,
      kernelVersion: String,
      osImage: String,
      containerRuntimeVersion: String,
      kubeletVersion: String,
      kubeProxyVersion: String)
}