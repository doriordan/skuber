package skuber.model

import Model._

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Node(
  	val kind: String ="Pod",
  	override val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    spec: Option[Node.Spec] = None,
    status: Option[Node.Spec] = None) 
      extends ObjectResource with KListItem

object Node {
   case class Spec(
      podCIDR: Option[String] = None,
      providerID: Option[String] = None,
      unschedulable: Boolean = false,
      externalID: Option[String] = None)
      
  case class Status(
      capacity: Option[Resource.ResourceList]=None,
      phase: Option[Phase] = None,
      conditions: Option[List[Node.Condition]] = None,
      addresses: Option[List[Node.Address]] = None,
      nodeInfo: Option[Node.SystemInfo] = None)
      
  sealed trait Phase
  case object Pending extends Phase
  case object Running extends Phase
  case object Teminated extends Phase
  
  case class Condition(
      _type : String, 
      status: String, 
      lastHeartbeatTime: Option[Date]=None,
      lastTransitionTime: Option[Date] = None,
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