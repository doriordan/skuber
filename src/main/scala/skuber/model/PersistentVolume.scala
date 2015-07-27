package skuber.model

import java.util.Date

import Model._
import Volume._

/**
 * @author David O'Riordan
 */
case class PersistentVolume(
    val kind: String ="PersistentVolume",
    override val apiVersion: String = "v1",
    val metadata: ObjectMeta = ObjectMeta(),
    spec: Option[PersistentVolume.Spec] = None,
    status: Option[PersistentVolume.Status] = None)
      extends ObjectResource 
   
object PersistentVolume {
  case class Spec(
      capacity: Resource.ResourceList,
      source: Volume.PersistentSource,
      accessModes: List[String] = List(),
      claimRef: ObjectReference,
      persistentVolumeReclaimPolicy: String)
      
  case class Status(
      phase: String = "",
      accessModes: List[String] = List())
}