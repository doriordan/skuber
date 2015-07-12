package skuber.model

import Model._

/**
 * @author David O'Riordan
 */
case class Namespace(
  	val kind: String ="Namespace",
  	val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    spec: Option[Namespace.Spec] = None,
    status: Option[Namespace.Status]= None) 
  extends ObjectResource

object Namespace {
  case class Spec(finalizers: List[String])
  case class Status(phase: String)
 
  val default = Namespace.use("default")
  def use(label: String) : Namespace = Namespace(metadata=ObjectMeta(name=label))
  def from(meta:ObjectMeta) : Namespace = Namespace(metadata=meta)
}