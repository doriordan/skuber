package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.json.format._

case class PersistentVolumeClaimSpecApplyConfig(
  accessModes: Option[List[PersistentVolume.AccessMode.AccessMode]] = None,
  resources: Option[Resource.Requirements] = None,
  volumeName: Option[String] = None,
  storageClassName: Option[String] = None,
  volumeMode: Option[PersistentVolumeClaim.VolumeMode.VolumeMode] = None,
  selector: Option[LabelSelector] = None
) {
  def withAccessModes(modes: List[PersistentVolume.AccessMode.AccessMode]): PersistentVolumeClaimSpecApplyConfig =
    copy(accessModes = Some(modes))
  def withStorageClassName(sc: String): PersistentVolumeClaimSpecApplyConfig = copy(storageClassName = Some(sc))
  def withVolumeName(vn: String): PersistentVolumeClaimSpecApplyConfig = copy(volumeName = Some(vn))
  def withStorageRequest(storage: Resource.Quantity): PersistentVolumeClaimSpecApplyConfig = {
    val currResources = resources.getOrElse(Resource.Requirements())
    val newReqs = currResources.requests + (Resource.storage -> storage)
    copy(resources = Some(Resource.Requirements(currResources.limits, newReqs)))
  }
  def withSelector(s: LabelSelector): PersistentVolumeClaimSpecApplyConfig = copy(selector = Some(s))
}

object PersistentVolumeClaimSpecApplyConfig {
  implicit val writes: OWrites[PersistentVolumeClaimSpecApplyConfig] = (
    (JsPath \ "accessModes").writeNullable[List[String]] and
    (JsPath \ "resources").writeNullable[Resource.Requirements] and
    (JsPath \ "volumeName").writeNullable[String] and
    (JsPath \ "storageClassName").writeNullable[String] and
    (JsPath \ "volumeMode").writeNullable[String] and
    (JsPath \ "selector").writeNullable[LabelSelector]
  )(s => (s.accessModes.map(_.map(_.toString)), s.resources, s.volumeName, s.storageClassName, s.volumeMode.map(_.toString), s.selector))
}

case class PersistentVolumeClaimApplyConfig(
  kind: String = "PersistentVolumeClaim",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[PersistentVolumeClaimSpecApplyConfig] = None
) extends ApplyConfiguration[PersistentVolumeClaim] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): PersistentVolumeClaimApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): PersistentVolumeClaimApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): PersistentVolumeClaimApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: PersistentVolumeClaimSpecApplyConfig): PersistentVolumeClaimApplyConfig = copy(spec = Some(s))
}

object PersistentVolumeClaimApplyConfig {
  def apply(name: String): PersistentVolumeClaimApplyConfig =
    PersistentVolumeClaimApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[PersistentVolumeClaimApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[PersistentVolumeClaimSpecApplyConfig]
  )(p => (p.kind, p.apiVersion, p.metadata, p.spec))
}
