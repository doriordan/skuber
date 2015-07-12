package skuber.model

import Model._

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Pod(
  	val kind: String ="Pod",
  	val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    spec: Option[Pod.Status] = None,
    status: Option[Pod.Status] = None) 
      extends ObjectResource with KListable

object Pod {
   case class Spec(
      containers: List[Container],
      volumes: Option[List[Volume]] = None,
      restartPolicy: Option[String] = None,
      terminationGracePeriodSeconds: Option[Int] = None,
      activeDeadlineSeconds: Option[Int] = None,
      dnsPolicy: Option[String] = None,
      nodeSelector: Option[Map[String, String]] = None,
      serviceAccountName: Option[String]= None,
      nodeName: Option[String] = None,
      hostNetwork: Option[Boolean] = None,
      imagePullSecrets: Option[List[LocalObjectReference]])
      
      
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
    val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    template: Option[Template.Spec] = None)
    extends ObjectResource
    
  object Template {
     case class Spec(
         metadata: Option[ObjectMeta] = None,
         spec: Option[Pod.Spec] = None)
   }  
}