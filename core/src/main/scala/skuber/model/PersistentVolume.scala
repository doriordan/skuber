 package skuber.model

import java.util.Date

import Volume._

/**
 * @author David O'Riordan
 */
case class PersistentVolume(
  kind: String ="PersistentVolume",
  apiVersion: String = v1,
  metadata: ObjectMeta = ObjectMeta(),
  spec: Option[PersistentVolume.Spec] = None,
  status: Option[PersistentVolume.Status] = None)
      extends ObjectResource
{
  
  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

}
   
object PersistentVolume {

  val specification=CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Cluster,
    names = ResourceSpecification.Names(
      plural="persistentvolumes",
      singular="persistentvolume",
      kind="PersistentVolume",
      shortNames=Nil
    )
  )
  implicit val pvDef = new ResourceDefinition[PersistentVolume] { def spec=specification }
  implicit val pvListDef = new ResourceDefinition[PersistentVolumeList] { def spec=specification }

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
    val Recycle, Retain, Delete = Value
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