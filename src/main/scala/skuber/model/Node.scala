package skuber.model

import Model._

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Node(
  	val kind: String ="Node",
  	override val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    spec: Option[Node.Spec] = None,
    status: Option[Node.Status] = None) 
      extends ObjectResource with KListItem

object Node {
   case class Spec(
      podCIDR: String = "",
      providerID: String = "",
      unschedulable: Boolean = false,
      externalID: String = "")
      
  case class Status(
      capacity: Option[Resource.ResourceList]=None,
      phase: Option[Phase] = None,
      conditions: Option[List[Node.Condition]] = None,
      addresses: Option[List[Node.Address]] = None,
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