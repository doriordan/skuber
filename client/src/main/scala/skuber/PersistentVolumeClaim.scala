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
      extends ObjectResource {
  
  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

}
   
object PersistentVolumeClaim {

  val specification=CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(
      plural="persistentvolumeclaims",
      singular="persistentvolumeclaim",
      kind="PersistentVolumeClaim",
      shortNames=List("pvc")
    )
  )
  implicit val pvcDef = new ResourceDefinition[PersistentVolumeClaim] { def spec=specification }
  implicit val pvcListDef = new ResourceDefinition[PersistentVolumeClaimList] { def spec=specification }

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