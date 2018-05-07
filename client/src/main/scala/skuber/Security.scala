package skuber

/**
 * @author David O'Riordan
 */

object Security {
  // Note: Context can be set at pod and/or container level but certain attributes are level-specific
  case class Context(
      capabilities: Option[Capabilities] = None, 
      privileged: Option[Boolean] = None,
      seLinuxOptions: Option[SELinuxOptions] = None,
      runAsUser: Option[Int] = None,
      runAsGroup: Option[Int] = None,
      runAsNonRoot: Option[Boolean] = None,
      fsGroup: Option[Int] = None,
      allowPrivilegeEscalation: Option[Boolean] = None
  )
    
  type Capability=String    
  case class Capabilities(
      add:  List[Capability] = Nil, 
      drop: List[Capability] = Nil)
  
  case class SELinuxOptions(
      user: String = "",
      role: String = "",
      _type: String = "",
      level: String = "")    
      
      
}    