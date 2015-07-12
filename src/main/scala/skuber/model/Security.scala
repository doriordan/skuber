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
      add:  Option[List[Capability]] = None, 
      drop: Option[List[Capability]] = None)
  
  case class SELinuxOptions(
      user: Option[String] = None,
      role: Option[String] = None,
      _type: Option[String] = None,
      level: Option[String] = None)    
      
      
}    