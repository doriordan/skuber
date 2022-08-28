package skuber

/**
 * @author David O'Riordan
 */
case class Volume(name: String,
                  source: Volume.Source)

object Volume {

  sealed trait Source

  case class GitRepo(repository: String,
                     revision: Option[String] = None,
                     directory: Option[String] = None)
    extends Source

  sealed trait VolumeProjection

  case class ProjectedVolumeSource(defaultMode: Option[Int] = None,
                                   sources: List[VolumeProjection]) extends Source

  case class SecretProjection(name: String,
                              items: Option[List[KeyToPath]] = None,
                              optional: Option[Boolean] = None) extends VolumeProjection

  case class ConfigMapProjection(name: String,
                                 items: Option[List[KeyToPath]] = None,
                                 optional: Option[Boolean] = None) extends VolumeProjection

  case class DownwardAPIProjection(items: List[DownwardApiVolumeFile] = List()) extends VolumeProjection

  case class ServiceAccountTokenProjection(audience: Option[String],
                                           expirationSeconds: Option[Int],
                                           path: String) extends VolumeProjection

  case class Secret(secretName: String,
                    items: Option[List[KeyToPath]] = None,
                    defaultMode: Option[Int] = None,
                    optional: Option[Boolean] = None)
    extends Source

  case class DownwardApiVolumeSource(defaultMode: Option[Int] = None,
                                     items: List[DownwardApiVolumeFile] = List())
    extends Source

  case class ConfigMapVolumeSource(name: String,
                                   items: List[KeyToPath] = List(),
                                   defaultMode: Option[Int] = None,
                                   optional: Option[Boolean] = None)
    extends Source

  case class PersistentVolumeClaimRef(claimName: String,
                                      readOnly: Boolean = false)
    extends Source

  case class EmptyDir(medium: StorageMedium = DefaultStorageMedium,
                      sizeLimit: Option[Resource.Quantity] = None)
    extends Source

  sealed trait PersistentSource extends Source

  case class GenericVolumeSource(json: String) extends PersistentSource

  case class HostPath(path: String,
                      `type`: Option[String] = None)
    extends PersistentSource

  case class GCEPersistentDisk(pdName: String,
                               fsType: String = "ext4",
                               partition: Int = 0,
                               readOnly: Boolean = false)
    extends PersistentSource

  case class AWSElasticBlockStore(volumeID: String,
                                  fsType: String = "ext4",
                                  partition: Int = 0,
                                  readOnly: Boolean = false)
    extends PersistentSource

  case class NFS(server: String,
                 path: String,
                 readOnly: Boolean = false)
    extends PersistentSource

  case class Glusterfs(endpointsName: String,
                       path: String,
                       readOnly: Boolean = false)
    extends PersistentSource

  case class RBD(monitors: List[String],
                 image: String,
                 fsType: String = "ext4",
                 pool: String = "rbd",
                 user: String = "admin",
                 keyring: String = "/etc/cepth/keyring",
                 secretRef: Option[LocalObjectReference] = None,
                 readOnly: Boolean = false)
    extends PersistentSource

  case class ISCSI(targetPortal: String,
                   iqn: String,
                   portals: List[String] = List(),
                   lun: Int = 0,
                   fsType: String = "ext4",
                   readOnly: Boolean = false)
    extends PersistentSource

  case class KeyToPath(key: String,
                       path: String)

  sealed trait StorageMedium

  case object DefaultStorageMedium extends StorageMedium

  case object MemoryStorageMedium extends StorageMedium

  case object HugePagesStorageMedium extends StorageMedium

  case class DownwardApiVolumeFile(fieldRef: ObjectFieldSelector,
                                   mode: Option[Int] = None,
                                   path: String,
                                   resourceFieldRef: Option[ResourceFieldSelector])

  case class ObjectFieldSelector(apiVersion: String = "v1",
                                 fieldPath: String)

  case class ResourceFieldSelector(containerName: Option[String],
                                   divisor: Option[Resource.Quantity],
                                   resource: String)

  object MountPropagationMode extends Enumeration {
    type MountPropagationMode = Value
    val HostToContainer, Bidirectional, None = Value
  }

  case class Mount(name: String,
                   mountPath: String,
                   readOnly: Boolean = false,
                   subPath: String = "",
                   mountPropagation: Option[MountPropagationMode.Value] = None)

  case class Device(name: String,
                    devicePath: String)
}
