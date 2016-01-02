package skuber.ext

import skuber._

import scala.reflect.ClassTag

/**
 * @author David O'Riordan
 */
case class Scale(
    val kind: String = "Scale",
    override val apiVersion: String = extensionsAPIVersion,
    val metadata: ObjectMeta,
    spec: Scale.Spec = Scale.Spec(),
    status: Option[Scale.Status] = None) extends ObjectResource {
  def withReplicas(count: Int) = this.copy(spec=Scale.Spec(count))
}
    
object Scale {
  def named(name: String) = new Scale(metadata=ObjectMeta(name=name))

  def scale(rc: ReplicationController) = new Scale(metadata=ObjectMeta(name=rc.name,namespace=rc.metadata.namespace))
  def scale(de: Deployment) = new Scale(metadata=ObjectMeta(name=de.name,namespace=de.metadata.namespace))
  
  case class Spec(replicas: Int = 0)
  case class Status(
    replicas: Int = 0,
    selector: Map[String, String] = Map()
  )
}    