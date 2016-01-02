package skuber

import java.util.Date

import Volume._

/**
 * @author David O'Riordan
 */
case class PersistentVolumeClaim(
    val kind: String ="PersistentVolumeClaim",
    override val apiVersion: String = v1,
    val metadata: ObjectMeta = ObjectMeta(),
    spec: Option[PersistentVolumeClaim.Spec] = None,
    status: Option[PersistentVolumeClaim.Status] = None)
      extends ObjectResource
   
object PersistentVolumeClaim {
  
  import PersistentVolume.AccessMode
  case class Spec(
      accessModes: List[AccessMode.AccessMode] = Nil,
      resources: Option[Resource.Requirements] = None,
      volumeName: String="")
      
  import PersistentVolume.Phase    
  case class Status(
      phase: Option[Phase.Phase] = None,
      accessModes: List[AccessMode.AccessMode] = List())
}