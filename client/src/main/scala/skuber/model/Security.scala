package skuber.model

/**
 * @author David O'Riordan
 */

object Security {
  case class Context(
      capabilities: Option[Capabilities] = None, 
      privileged: Option[Boolean] = None,
      seLinuxOptions: Option[SELinuxOptions] = None,
      runAsUser: Option[Int] = None)
    
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