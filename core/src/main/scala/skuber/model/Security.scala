package skuber.model

/**
  * @author David O'Riordan
  */

case class SecurityContext(
  allowPrivilegeEscalation: Option[Boolean] = None,
  capabilities: Option[Security.Capabilities] = None,
  privileged: Option[Boolean] = None,
  readOnlyRootFilesystem: Option[Boolean] = None,
  runAsGroup: Option[Int] = None,
  runAsNonRoot: Option[Boolean] = None,
  runAsUser: Option[Int] = None,
  seLinuxOptions: Option[Security.SELinuxOptions] = None
)

case class PodSecurityContext(
  fsGroup: Option[Int] = None,
  runAsGroup: Option[Int] = None,
  runAsNonRoot: Option[Boolean] = None,
  runAsUser: Option[Int] = None,
  seLinuxOptions: Option[Security.SELinuxOptions] = None,
  supplementalGroups: List[Int] = Nil,
  sysctls: List[Security.Sysctl] = Nil
)

object Security {
  type Capability = String

  case class Capabilities(add: List[Capability] = Nil, drop: List[Capability] = Nil)

  case class SELinuxOptions(user: String = "", role: String = "", _type: String = "", level: String = "")

  case class Sysctl(name: String, value: String)

}