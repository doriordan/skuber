package skuber.model

/**
 * @author David O'Riordan
 */
case class EnvVar(
    name: String,
    value: EnvVar.Value = "")

object EnvVar {
  sealed trait Value
  case class StringValue(s:String) extends Value
  case class Source(fieldRef: FieldSelector) extends Value
  case class FieldSelector(fieldRef: String, apiVersion: Option[String] = None )
  
  implicit def strToValue(s:String) : Value = StringValue(s)
    
}    