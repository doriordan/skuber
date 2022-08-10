package skuber

/**
  * @author David O'Riordan
  */

case class EnvFromSource(prefix: Option[String] = None,
  source:EnvFromSource.EnvSource
)

object EnvFromSource {

  sealed trait EnvSource {
    val name: String
    val optional: Option[Boolean]
  }
  case class ConfigMapEnvSource(name: String, optional: Option[Boolean] = None) extends EnvSource
  case class SecretEnvSource(name: String, optional: Option[Boolean] = None) extends EnvSource

}
