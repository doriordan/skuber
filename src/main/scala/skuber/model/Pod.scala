package skuber.model

import Model._

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Pod(
  	val kind: String ="Pod",
  	override val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    spec: Option[Pod.Spec] = None,
    status: Option[Pod.Status] = None) 
      extends ObjectResource with KListItem

object Pod {
   case class Spec(
      containers: List[Container] = List(),
      volumes: Option[List[Volume]] = None,
      restartPolicy: Option[String] = None,
      terminationGracePeriodSeconds: Option[Int] = None,
      activeDeadlineSeconds: Option[Int] = None,
      dnsPolicy: Option[String] = None,
      nodeSelector: Option[Map[String, String]] = None,
      serviceAccountName: Option[String]= None,
      nodeName: Option[String] = None,
      hostNetwork: Option[Boolean] = None,
      imagePullSecrets: Option[List[LocalObjectReference]] = None) {
     
     // fluent pod spec builder API methods
     def addContainer(c: Container) = { this.copy(containers = c :: containers) }
     def addVolume(v: Volume) = { this.copy(volumes = Some(v :: volumes.getOrElse(List[Volume]()))) }
     def withRestartPolicy(r: String) = { this.copy(restartPolicy=Some(r)) }
     def withTermGP(t: Int) = { this.copy(terminationGracePeriodSeconds = Some(t))}
     def withActiveDL(t: Int) = { this.copy(activeDeadlineSeconds=Some(t))}
     def withDNSPolicy(p: String) = {this.copy(dnsPolicy=Some(p))}
     def addNodeSelector(kv: Tuple2[String,String]) = {
       this.copy(nodeSelector = Some(nodeSelector.getOrElse(Map[String,String]()) + kv))
     }
     def withServiceAccountName(n: String) = { this.copy(serviceAccountName = Some(n))}
     def withNodeName(n: String) = { this.copy(nodeName = Some(n))}
     def requestHostNetworking(b: Boolean) = { this.copy(hostNetwork = Some(b))}
     def addImagePullSecretRef(ref: String) = {
       val loref = LocalObjectReference(ref)
       this.copy(imagePullSecrets = 
         Some(loref :: imagePullSecrets.getOrElse(List[LocalObjectReference]()))) 
     }
   }
      
      
  case class Status(
      phase: Option[String],
      conditions: Option[Condition],
      message: Option[String],
      reason: Option[String],
      hostIP: Option[String],
      podIP: Option[String],
      startTime: Option[Date],
      containerStatuses: Option[List[Container.Status]])
      
  case class Condition(_type : String, status: String)
 
  case class Template(
    val kind: String ="PodTemplate",
    override val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    template: Option[Template.Spec] = None)
    extends ObjectResource
    
  object Template {
     case class Spec(
         metadata: Option[ObjectMeta] = None,
         spec: Option[Pod.Spec] = None)
   }  
}