package skuber

import skuber.annotation.MatchExpression

/**
  * @author David O'Riordan
  *
  */

case class PersistentVolumeClaim(val kind: String = "PersistentVolumeClaim",
    override val apiVersion: String = v1,
    val metadata: ObjectMeta = ObjectMeta(),
    spec: Option[PersistentVolumeClaim.Spec] = None,
    status: Option[PersistentVolumeClaim.Status] = None)
  extends ObjectResource {

  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion = version))

}

case class Selector(matchLabels: Option[Map[String, String]] = None, matchExpressions: Option[List[MatchExpression]] = None)

object PersistentVolumeClaim {

  object VolumeMode extends Enumeration {
    type VolumeMode = Value
    val Filesystem, BlockVolume = Value
  }

  val specification = CoreResourceSpecification(scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(plural = "persistentvolumeclaims",
      singular = "persistentvolumeclaim",
      kind = "PersistentVolumeClaim",
      shortNames = List("pvc")))
  implicit val pvcDef: ResourceDefinition[PersistentVolumeClaim] = new ResourceDefinition[PersistentVolumeClaim] { val spec = specification }
  implicit val pvcListDef: ResourceDefinition[PersistentVolumeClaimList] = new ResourceDefinition[PersistentVolumeClaimList] { val spec = specification }

  import PersistentVolume.AccessMode
  case class Spec(accessModes: List[AccessMode.AccessMode] = Nil,
    resources: Option[Resource.Requirements] = None,
    volumeName: Option[String] = None,
    storageClassName: Option[String] = None,
    volumeMode: Option[VolumeMode.VolumeMode] = None,
    selector: Option[Selector] = None)

  import PersistentVolume.Phase
  case class Status(phase: Option[Phase.Phase] = None,
    accessModes: List[AccessMode.AccessMode] = List())

}
