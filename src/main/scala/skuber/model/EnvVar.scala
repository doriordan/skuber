package skuber.model

/**
 * @author David O'Riordan
 */
case class EnvVar(
    name: String,
    value: EnvVar.Value = Left(""))

object EnvVar {
  type Value = Either[String, Source]
  case class Source(fieldRef: FieldSelector)
  case class FieldSelector(fieldRef: String, apiVersion: Option[String] = None )
    
}    