package skuber.api

import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * @author David O'Riordan
 */

import skuber.model.Model._
import skuber.model.Volume
import skuber.model.Volume._
import JsonReadWrite._

object VolumeReadWrite {
   implicit lazy val emptyDirFormat = Json.format[EmptyDir]
   implicit lazy val hostPathFormat = Json.format[HostPath]  
   implicit lazy val secretFormat = Json.format[Secret]
   implicit lazy val gitFormat = Json.format[GitRepo]
   
   implicit lazy val gceFormat: Format[GCEPersistentDisk] = (
     (JsPath \ "pdName").format[String] and
     (JsPath \ "fsType").format[String] and 
     (JsPath \ "partition").formatMaybeEmptyInt() and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(GCEPersistentDisk.apply _, unlift(GCEPersistentDisk.unapply))
   
   implicit lazy val awsFormat: Format[AWSElasticBlockStore] = (
     (JsPath \ "volumeID").format[String] and
     (JsPath \ "fsType").format[String] and 
     (JsPath \ "partition").formatMaybeEmptyInt() and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(AWSElasticBlockStore.apply _, unlift(AWSElasticBlockStore.unapply))
   
    implicit lazy val nfsFormat: Format[NFS] = (
     (JsPath \ "server").format[String] and
     (JsPath \ "path").format[String] and 
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(NFS.apply _, unlift(NFS.unapply))
   
   implicit lazy val glusterfsFormat: Format[Glusterfs] = (
     (JsPath \ "server").format[String] and
     (JsPath \ "path").format[String] and 
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(Glusterfs.apply _, unlift(Glusterfs.unapply))
   
   implicit lazy val rdbFormat: Format[RDB] = (
     (JsPath \ "monitors").format[List[String]] and
     (JsPath \ "image").format[String] and 
     (JsPath \ "fsType").format[String] and 
     (JsPath \ "pool").formatMaybeEmptyString() and 
     (JsPath \ "user").formatMaybeEmptyString() and
     (JsPath \ "keyring").formatMaybeEmptyString() and
     (JsPath \ "secretRef").formatNullable[LocalObjectReference] and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(RDB.apply _, unlift(RDB.unapply))
   
    implicit lazy val iscsiFormat: Format[ISCSI] = (
     (JsPath \ "targetPortal").format[String] and 
     (JsPath \ "iqn").format[String] and 
     (JsPath \ "lun").format[Int] and 
     (JsPath \ "fsType").format[String] and 
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(ISCSI.apply _, unlift(ISCSI.unapply))
   
     
   implicit lazy val persistentVolumeClaimFormat: Format[PersistentVolumeClaim] = (
     (JsPath \ "claimName").format[String] and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(PersistentVolumeClaim.apply _, unlift(PersistentVolumeClaim.unapply))
   
   implicit lazy val volumeSourceReads: Reads[Source] = (
     (JsPath \ "emptyDir").read[EmptyDir].map(x => x: Source) |
     (JsPath \ "hostPath").read[HostPath].map(x => x: Source) |
     (JsPath \ "secret").read[Secret].map(x => x:Source) |
     (JsPath \ "gitRepo").read[GitRepo].map(x => x:Source) |
     (JsPath \ "gcePersistentDisk").read[GCEPersistentDisk].map(x => x:Source) |
     (JsPath \ "awsElasticBlockStore").read[AWSElasticBlockStore].map(x => x: Source) |
     (JsPath \ "nfs").read[NFS].map(x => x: Source) |
     (JsPath \ "glusterfs").read[Glusterfs].map(x => x: Source) |
     (JsPath \ "rdb").read[RDB].map(x => x: Source) |
     (JsPath \ "iscsi").read[ISCSI].map(x => x: Source) |
     (JsPath \ "persistentVolumeClaim").read[PersistentVolumeClaim].map(x => x: Source)
   )
   
   implicit lazy val volumeSourceWrites: Writes[Source] = Writes[Source] { 
     source => source match {
       case ed: EmptyDir => (JsPath \ "emptyDir").write[EmptyDir](emptyDirFormat).writes(ed)
       case hp: HostPath => (JsPath \ "hostPath").write[HostPath](hostPathFormat).writes(hp)
       case secr: Secret => (JsPath \ "secret").write[Secret](secretFormat).writes(secr) 
       case gitr: GitRepo => (JsPath \ "gitRepo").write[GitRepo](gitFormat).writes(gitr)
       case gced: GCEPersistentDisk => (JsPath \ "gcePersistentDisk").write[GCEPersistentDisk](gceFormat).writes(gced)
       case awse: AWSElasticBlockStore => (JsPath \ "awsElasticBlockStore").write[AWSElasticBlockStore](awsFormat).writes(awse)
       case nfs: NFS => (JsPath \ "nfs").write[NFS](nfsFormat).writes(nfs)
       case gfs: Glusterfs => (JsPath \ "glusterfs").write[Glusterfs](glusterfsFormat).writes(gfs)
       case rdb: RDB => (JsPath \ "rdb").write[RDB](rdbFormat).writes(rdb) 
       case iscsi: ISCSI => (JsPath \ "iscsi").write[ISCSI](iscsiFormat).writes(iscsi) 
       case pvc: PersistentVolumeClaim => (JsPath \ "persistentVolumeClaim").write[PersistentVolumeClaim](persistentVolumeClaimFormat).writes(pvc) 
     }
   }
  
   implicit lazy val volumeReads: Reads[Volume] = (
     (JsPath \ "name").read[String] and
     volumeSourceReads
   )(Volume.apply _)
        
   implicit lazy val volumeWrites: Writes[Volume] = (
     (JsPath \ "name").write[String] and 
     JsPath.write[Source]
   )(unlift(Volume.unapply))
   
} 
  
