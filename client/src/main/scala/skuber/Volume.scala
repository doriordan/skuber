package skuber

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Volume(
    name: String,
    source: Volume.Source) 

object Volume {
  
  sealed trait Source 
  case class GitRepo(repository: String, revision: Option[String] = None)  extends Source
  case class Secret(secretName: String)  extends Source 
  
  case class ConfigMapVolumeSource(defaultMode: Int, name: String, items: List[KeyToPath] = List())  extends Source
  case class KeyToPath(key: String, mode: Int, path: String)

  sealed trait StorageMedium
  case object DefaultStorageMedium extends StorageMedium
  case object MemoryStorageMedium extends StorageMedium
  case class EmptyDir(medium: StorageMedium = DefaultStorageMedium) extends Source 
    
  sealed trait PersistentSource extends Source
  case class HostPath(path: String) extends PersistentSource
  case class GCEPersistentDisk(
      pdName: String, 
      fsType: String="ext4", 
      partition: Int = 0, 
      readOnly: Boolean = false) 
    extends PersistentSource
  case class AWSElasticBlockStore(
      volumeID: String, 
      fsType: String = "ext4", 
      partition: Int = 0, 
      readOnly: Boolean = false) 
    extends PersistentSource
  case class NFS(server: String, path: String, readOnly: Boolean = false) 
    extends PersistentSource
  case class Glusterfs(endpointsName: String, path: String, readOnly: Boolean = false) 
    extends PersistentSource
  case class RBD(
      monitors: List[String], 
      image: String, 
      fsType: String = "ext4", 
      pool: String = "rbd", 
      user: String = "admin",
      keyring: String = "/etc/cepth/keyring",
      secretRef: Option[LocalObjectReference] = None,
      readOnly: Boolean = false) 
    extends PersistentSource
  case class ISCSI(
      targetPortal: String, 
      iqn: String, 
      lun: Int = 0, 
      fsType: String = "ext4", 
      readOnly: Boolean = false) 
    extends PersistentSource
  case class PersistentVolumeClaimRef(claimName: String,readOnly: Boolean = false) 
    extends Source
  case class Mount(name: String, mountPath: String, readOnly: Boolean = false, subPath: String = "")
}
