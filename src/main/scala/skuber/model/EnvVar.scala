package skuber.model

/**
 * @author David O'Riordan
 */
case class EnvVar(
    name: String,
    value: Option[String] = None,
    valueFrom: Option[EnvVar.Source] = None)

object EnvVar {
  case class Source(fieldRef: FieldSelector)
  case class FieldSelector(fieldRef: String, apiVersion: Option[String] = None )
    
}    