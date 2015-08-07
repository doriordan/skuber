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
  
   def forNameAndSpec(name: String, spec: Pod.Spec) = Pod(metadata=ObjectMeta(name=name), spec = Some(spec))
  
   import DNSPolicy._
   case class Spec(
      volumes: List[Volume], 
      containers: List[Container], // should have at least one member
      restartPolicy: String = "",
      terminationGracePeriodSeconds: Option[Int] = None,
      activeDeadlineSeconds: Option[Int] = None,
      dnsPolicy: DNSPolicy = Default,
      nodeSelector: Map[String, String] = Map(),
      serviceAccountName: String,
      nodeName: String = "",
      hostNetwork: Boolean = false,
      imagePullSecrets: List[LocalObjectReference] = List()) {
     
     // a few convenience methods for fluently building out a pod spec
     def addContainer(c: Container) = { this.copy(containers = c :: containers) }
     def addVolume(v: Volume) = { this.copy(volumes = v :: volumes) }
     def addNodeSelector(kv: Tuple2[String,String]) = {
       this.copy(nodeSelector = this.nodeSelector + kv)
     }
     def addImagePullSecretRef(ref: String) = {
       val loref = LocalObjectReference(ref)
       this.copy(imagePullSecrets = loref :: this.imagePullSecrets)
     }
   }
      
      
  case class Status(
      phase: String,
      conditions: List[Condition],
      message: Option[String],
      reason: Option[String],
      hostIP: Option[String],
      podIP: Option[String],
      startTime: Option[Timestamp] = None,
      containerStatuses: List[Container.Status])
      
  case class Condition(_type : String="Ready", status: String)
 
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