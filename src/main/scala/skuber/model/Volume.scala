package skuber.model

import java.util.Date

import Model._

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
  case class EmptyDir(medium: String) extends Source
    
  sealed trait PersistentSource extends Source
  case class HostPath(path: String) extends PersistentSource
  case class GCEPersistentDisk(
      pdName: String, 
      fsType: String, 
      partition: Option[Int] = None, 
      readOnly: Boolean = false) 
    extends PersistentSource
  case class AWSElasticBlockStore(
      volumeID: String, 
      fsType: String, 
      partition: Option[Int] = None, 
      readOnly: Boolean = false) 
    extends PersistentSource
  case class NFS(server: String, path: String, readOnly: Option[Boolean] = None) 
    extends PersistentSource
  case class Glusterfs(endpoints: String, path: String, readOnly: Option[Boolean] = None) 
    extends PersistentSource
  case class RDB(
      monitors: List[String], 
      image: String, 
      fsType: String, 
      pool: Option[String] = None, 
      user: Option[String] = None,
      keyring: Option[String] = None,
      secretRef: Option[LocalObjectReference] = None,
      readOnly: Option[Boolean] = None) 
    extends PersistentSource
  case class ISCSI(
      targetPortal: String, 
      iqn: String, 
      lun: Int, 
      fsType: String, 
      readOnly: Option[Boolean] = None) 
    extends PersistentSource
  case class PersistentVolumeClaim(claimName: String,readOnly: Option[Boolean] = None) 
    extends Source
  case class Mount(name: String, mountPath: String, readOnly: Option[Boolean] = None)
}