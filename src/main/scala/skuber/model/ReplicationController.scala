package skuber.model

import Model._

import java.util.Date

/**
 * @author David O'Riordan
 */
case class ReplicationController(
  	val kind: String ="ReplicationController",
  	val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    spec: Option[ReplicationController.Spec] = None,
    status: Option[ReplicationController.Status] = None) 
      extends ObjectResource with KListable

object ReplicationController {
    case class Spec(
      replicas: Option[Int] = None,
      selector: Option[Map[String, String]] = None,
      template: Option[Pod.Template.Spec] = None)
      
  case class Status(
      replicas: Int,
      observerdGeneration: Option[Int])
}