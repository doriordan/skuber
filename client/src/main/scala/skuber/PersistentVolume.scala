package skuber

import java.util.Date

import Volume._

/**
 * @author David O'Riordan
 */
case class PersistentVolume(
    val kind: String ="PersistentVolume",
    override val apiVersion: String = v1,
    val metadata: ObjectMeta = ObjectMeta(),
    spec: Option[PersistentVolume.Spec] = None,
    status: Option[PersistentVolume.Status] = None)
      extends ObjectResource
   
object PersistentVolume {
  
  object AccessMode extends Enumeration {
    type AccessMode = Value
    val ReadWriteOnce,ReadOnlyMany,ReadWriteMany = Value
  }
  
  object Phase extends Enumeration {
    type Phase = Value
    val Pending, Available, Bound, Released, Failed = Value
  }
  
  object ReclaimPolicy extends Enumeration {
    type ReclaimPolicy = Value
    val Recycle, Retain = Value
  }
  
  case class Spec(
      capacity: Resource.ResourceList,
      source: Volume.PersistentSource,
      accessModes: List[AccessMode.AccessMode] = List(),
      claimRef: Option[ObjectReference] = None,
      persistentVolumeReclaimPolicy: Option[ReclaimPolicy.ReclaimPolicy] = None)
      
  case class Status(
      phase: Option[Phase.Phase] = None,
      accessModes: List[AccessMode.AccessMode] = List())
}