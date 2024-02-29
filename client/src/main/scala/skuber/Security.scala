package skuber

/**
 * @author David O'Riordan
 */

import Security._

case class SecurityContext(
  allowPrivilegeEscalation: Option[Boolean]        = None,
  capabilities:             Option[Capabilities]   = None,
  privileged:               Option[Boolean]        = None,
  readOnlyRootFilesystem:   Option[Boolean]        = None,
  runAsGroup:               Option[Int]            = None,
  runAsNonRoot:             Option[Boolean]        = None,
  runAsUser:                Option[Int]            = None,
  seLinuxOptions:           Option[SELinuxOptions] = None
)

case class PodSecurityContext(
  fsGroup:            Option[Int]            = None,
  runAsGroup:         Option[Int]            = None,
  runAsNonRoot:       Option[Boolean]        = None,
  runAsUser:          Option[Int]            = None,
  seLinuxOptions:     Option[SELinuxOptions] = None,
  supplementalGroups: List[Int]              = Nil,
  sysctls:            List[Sysctl]           = Nil,
  seccompProfile:     Option[SeccompProfile] = None
)

object Security {
  type Capability = String
  type SeccompProfileType = String

  case class Capabilities(add: List[Capability] = Nil, drop: List[Capability] = Nil)

  case class SELinuxOptions(user: String = "", role: String = "", _type: String = "", level: String = "")

  case class Sysctl(name: String, value: String)

  sealed trait SeccompProfile {
    val _type: SeccompProfileType
  }
  case class UnconfinedProfile() extends SeccompProfile {
    override val _type: SeccompProfileType = "Unconfined"
  }
  case class RuntimeDefaultProfile() extends SeccompProfile {
    override val _type: SeccompProfileType = "RuntimeDefault"
  }
  case class LocalhostProfile(localhostProfile: String) extends SeccompProfile {
    override val _type: SeccompProfileType = "Localhost"
  }
  case class UnknownProfile() extends SeccompProfile {
    override val _type: SeccompProfileType = "Unknown"
  }

}
