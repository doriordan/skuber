package skuber

import java.io._
import org.apache.commons.io.IOUtils

/**
 * @author David O'Riordan
 */
case class Secret(
    val kind: String ="Secret",
    override val apiVersion: String = v1,
    val metadata: ObjectMeta,
    data: Map[String, Array[Byte]] = Map(),
    val `type`: String = "")
  extends ObjectResource  {
  
    def add(key: String, is: InputStream) : Unit = {
       val bytes = IOUtils.toByteArray(is)
       add(key, bytes) 
    }
    def add(key: String, bytes: Array[Byte]) : Unit =
      this.copy(data = data + (key -> bytes))
} 

object Secret {

  val specification=CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(
      plural="secrets",
      singular="secret",
      kind="Secret",
      shortNames=Nil
    )
  )
  implicit val secDef = new ResourceDefinition[Secret] { def spec=specification }
  implicit val secListDef = new ResourceDefinition[SecretList] { def spec=specification }
}
