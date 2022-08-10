package skuber

/**
 * @author David O'Riordan
 */
case class EnvVar(name: String,
    value: EnvVar.Value = "")

object EnvVar {
  sealed trait Value
  case class StringValue(s:String) extends Value
  sealed trait Source extends Value
  case class FieldRef(fieldPath: String, apiVersion: String = "") extends Source
  case class ConfigMapKeyRef(key: String = "", name: String="") extends Source
  case class SecretKeyRef(key: String="", name: String = "") extends Source

  import scala.language.implicitConversions
  implicit def strToValue(s:String) : Value = StringValue(s)
}    