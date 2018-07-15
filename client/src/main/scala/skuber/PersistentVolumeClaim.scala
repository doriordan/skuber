package skuber

import Volume._

/**
  * @author David O'Riordan
  */
case class PersistentVolumeClaim(
    kind: String = "PersistentVolumeClaim",
    override val apiVersion: String = v1,
    metadata: ObjectMeta = ObjectMeta(),
    spec: Option[PersistentVolumeClaim.Spec] = None,
    status: Option[PersistentVolumeClaim.Status] = None
) extends ObjectResource {

  def withResourceVersion(version: String): PersistentVolumeClaim =
    this.copy(metadata = metadata.copy(resourceVersion = version))

}

object PersistentVolumeClaim {

  val specification = CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(
      plural = "persistentvolumeclaims",
      singular = "persistentvolumeclaim",
      kind = "PersistentVolumeClaim",
      shortNames = List("pvc")
    )
  )
  implicit val pvcDef: ResourceDefinition[PersistentVolumeClaim] = new ResourceDefinition[PersistentVolumeClaim] {
    def spec: CoreResourceSpecification = specification
  }
  implicit val pvcListDef: ResourceDefinition[PersistentVolumeClaimList] =
    new ResourceDefinition[PersistentVolumeClaimList] { def spec: CoreResourceSpecification = specification }

  import PersistentVolume.AccessMode
  case class Spec(
      accessModes: List[AccessMode.AccessMode] = Nil,
      resources: Option[Resource.Requirements] = None,
      volumeName: String = ""
  )

  import PersistentVolume.Phase
  case class Status(phase: Option[Phase.Phase] = None, accessModes: List[AccessMode.AccessMode] = List())
}
