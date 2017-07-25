package skuber.json

import java.time._
import java.time.format._

import skuber._

import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError

import org.apache.commons.codec.binary.Base64

import scala.language.implicitConversions

/**
 * @author David O'Riordan
 * Play/json formatters for the Skuber k8s model types
 */
package object format {
  
  // Formatters for the Java 8 ZonedDateTime objects that represent
  // (ISO 8601 / RFC 3329 compatible) Kubernetes timestamp fields 
  implicit val timewWrites = Writes.temporalWrites[ZonedDateTime, DateTimeFormatter](
      DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  implicit val timeReads = Reads.DefaultZonedDateTimeReads    
      
  // Many Kubernetes fields are interpreted as "empty" if they have certain values:
  // Strings: zero length
  // Integers : 0 
  // Boolean: false
  // These fields may be omitted from the json objects if they have the above values, the formatters below support this
  
  class MaybeEmpty(val path: JsPath) {
    def formatMaybeEmptyString(omitEmpty: Boolean=true): OFormat[String] =
      path.formatNullable[String].inmap[String](_.getOrElse(emptyS), s => if (omitEmpty && s.isEmpty) None else Some(s) )
      
    def formatMaybeEmptyList[T](implicit tReads: Reads[T], tWrites: Writes[T], omitEmpty: Boolean=true) : OFormat[List[T]] =
      path.formatNullable[List[T]].inmap[List[T]](_.getOrElse(emptyL[T]), l => if (omitEmpty && l.isEmpty) None else Some(l))
      
     def formatMaybeEmptyMap[V](
         implicit  vReads: Reads[V],  vWrites: Writes[V],
         omitEmpty: Boolean=true) : OFormat[Map[String,V]] =
      path.formatNullable[Map[String,V]].inmap[Map[String,V]](_.getOrElse(emptyM[V]), m => if (omitEmpty && m.isEmpty) None else Some(m))

    def formatMaybeEmptyByteArrayMap(
         implicit vReads: Reads[Map[String, Array[Byte]]], vWrites: Writes[Map[String, Array[Byte]]],
         omitEmpty: Boolean=true) : OFormat[Map[String,Array[Byte]]] =
      path.formatNullable[Map[String,Array[Byte]]].inmap[Map[String,Array[Byte]]](_.getOrElse(emptyM[Array[Byte]]), m => if (omitEmpty && m.isEmpty) None else Some(m))

    // Boolean: the empty value is 'false'  
    def formatMaybeEmptyBoolean(omitEmpty: Boolean=true) : OFormat[Boolean] =
      path.formatNullable[Boolean].inmap[Boolean](_.getOrElse(false), b => if (omitEmpty && !b) None else Some(b))
      
    // Int: the empty value is 0
    def formatMaybeEmptyInt(omitEmpty: Boolean=true) : OFormat[Int] =
      path.formatNullable[Int].inmap[Int](_.getOrElse(0), i => if (omitEmpty && i==0) None else Some(i))
      
    // Int Or String: the emoty value is the specified default  
    def formatMaybeEmptyIntOrString(empty: IntOrString): OFormat[IntOrString] =
      path.formatNullable[IntOrString].inmap[IntOrString](_.getOrElse(empty), i => Some(i))
  }
  
  // we make the above formatter methods available on JsPath objects via this implicit conversion
  implicit def jsPath2MaybeEmpty(path: JsPath) = new MaybeEmpty(path)
   
  // general formatting for Enumerations - derived from https://gist.github.com/mikesname/5237809
  implicit def enumReads[E <: Enumeration](enum: E) : Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) => {
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException => JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not appear to contain the value: '$s'")
        }
      }
      case _ => JsError("String value expected")
    } 
  }
  
  implicit def enumReads[E <: Enumeration](enum: E, default: E#Value) : Reads[E#Value] = enumReads(enum) or Reads.pure(default)
  
  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enum: E) : Format[E#Value] = Format(enumReads(enum), enumWrites)
   
  class EnumFormatter(val path: JsPath) {
     def formatEnum[E <: Enumeration](enum: E, default: Option[E#Value]=None) : OFormat[E#Value] =
        path.formatNullable[String].inmap[E#Value](_.map(s => enum.withName(s)).getOrElse(default.get), e =>  Some(e.toString))
     def formatNullableEnum[E <: Enumeration](enum: E)  : OFormat[Option[E#Value]] =
        path.formatNullable[String].inmap[Option[E#Value]](_.map(s => enum.withName(s)), e => e map { _.toString } )
  }
  implicit def jsPath2enumFmtr(path: JsPath)  = new EnumFormatter(path)
   

  // formatting of the Kubernetes types
  
  implicit lazy val objFormat =
    (JsPath \ "kind").formatMaybeEmptyString() and 
    (JsPath \ "apiVersion").formatMaybeEmptyString() and 
    (JsPath \ "metadata").lazyFormat[ObjectMeta](objectMetaFormat) 
   // metadata format must be lazy as it can be used in indirectly recursive namespace structure (Namespace has a metadata.namespace field)
    
  def KListFormat[K <: KListItem](implicit f: Format[K]) =
    (JsPath \ "kind").format[String] and
    (JsPath \ "apiVersion").format[String] and
    (JsPath \ "metadata").formatNullable[ListMeta] and
    (JsPath \ "items").formatMaybeEmptyList[K]
  
  implicit lazy val objectMetaFormat: Format[ObjectMeta] = (
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "generateName").formatMaybeEmptyString() and 
    (JsPath \ "namespace").formatMaybeEmptyString() and
    (JsPath \ "uid").formatMaybeEmptyString() and
    (JsPath \ "selfLink").formatMaybeEmptyString() and
    (JsPath \ "resourceVersion").formatMaybeEmptyString() and
    (JsPath \ "creationTimestamp").formatNullable[Timestamp] and
    (JsPath \ "deletionTimestamp").formatNullable[Timestamp] and
    (JsPath \ "labels").formatMaybeEmptyMap[String] and
    (JsPath \ "annotations").formatMaybeEmptyMap[String] and
    (JsPath \ "generation").formatMaybeEmptyInt()
  )(ObjectMeta.apply _, unlift(ObjectMeta.unapply))
    
  implicit val listMetaFormat: Format[ListMeta] = (
    (JsPath \ "selfLink").formatMaybeEmptyString() and
    (JsPath \ "resourceVersion").formatMaybeEmptyString()
  )(ListMeta.apply _, unlift(ListMeta.unapply))
  
  implicit val localObjRefFormat = Json.format[LocalObjectReference]
  
  implicit val apiVersionsFormat = Json.format[APIVersions]
  
  implicit val objRefFormat: Format[ObjectReference] = (
    (JsPath \ "kind").formatMaybeEmptyString() and
    (JsPath \ "apiVersion").formatMaybeEmptyString() and
    (JsPath \ "namespace").formatMaybeEmptyString() and
    (JsPath \ "name").formatMaybeEmptyString() and  
    (JsPath \ "uid").formatMaybeEmptyString() and
    (JsPath \ "resourceVersion").formatMaybeEmptyString() and
    (JsPath \ "fieldPath").formatMaybeEmptyString()
  )(ObjectReference.apply _, unlift(ObjectReference.unapply))
  
  implicit val nsSpecFormat: Format[Namespace.Spec] = Json.format[Namespace.Spec]
  implicit val nsStatusFormat: Format[Namespace.Status] = Json.format[Namespace.Status]
  
  implicit lazy val namespaceFormat : Format[Namespace] = (
      objFormat and
    	(JsPath \ "spec").formatNullable[Namespace.Spec] and
    	(JsPath \ "status").formatNullable[Namespace.Status]
    ) (Namespace.apply _, unlift(Namespace.unapply))	
 
  implicit val secSELFormat: Format[Security.SELinuxOptions] = (
      (JsPath \ "user").formatMaybeEmptyString() and
      (JsPath \ "role").formatMaybeEmptyString() and
      (JsPath \ "type").formatMaybeEmptyString() and
      (JsPath \ "level").formatMaybeEmptyString()
   )(Security.SELinuxOptions.apply _, unlift(Security.SELinuxOptions.unapply))
   
  implicit val secCapabFormat: Format[Security.Capabilities] = (
      (JsPath \ "add").formatMaybeEmptyList[Security.Capability] and
      (JsPath \ "drop").formatMaybeEmptyList[Security.Capability]
  )(Security.Capabilities.apply _, unlift(Security.Capabilities.unapply))
 
  implicit val secCtxtFormat: Format[Security.Context] = Json.format[Security.Context]
 
  implicit val envVarFldRefFmt = Json.format[EnvVar.FieldRef]
  implicit val envVarCfgMapRefFmt = Json.format[EnvVar.ConfigMapKeyRef]
  implicit val envVarSecKeyRefFmt = Json.format[EnvVar.SecretKeyRef]
  
  implicit val envVarValueWrite = Writes[EnvVar.Value] { 
     value => value match {
       case EnvVar.StringValue(str) => (JsPath \ "value").write[String].writes(str)
       case fr: EnvVar.FieldRef => (JsPath \ "valueFrom" \ "fieldRef").write[EnvVar.FieldRef].writes(fr)
       case cmr: EnvVar.ConfigMapKeyRef => (JsPath \ "valueFrom" \ "configMapKeyRef").write[EnvVar.ConfigMapKeyRef].writes(cmr)
       case skr: EnvVar.SecretKeyRef => (JsPath \ "valueFrom" \ "secretKeyRef").write[EnvVar.SecretKeyRef].writes(skr)
     }
  }
  
  implicit val envVarValueReads: Reads[EnvVar.Value] = (
      (JsPath \ "value").readNullable[String].map(value => EnvVar.StringValue(value.getOrElse(""))) |
      (JsPath \ "valueFrom" \ "fieldRef").read[EnvVar.FieldRef].map(x => x: EnvVar.Value) |
      (JsPath \ "valueFrom" \ "configMapKeyRef").read[EnvVar.ConfigMapKeyRef].map(x => x: EnvVar.Value) |
      (JsPath \ "valueFrom" \ "secretKeyRef").read[EnvVar.SecretKeyRef].map(x => x: EnvVar.Value)
  )
  
   implicit val envVarWrites : Writes[EnvVar] = (
    (JsPath \ "name").write[String] and
    JsPath.write[EnvVar.Value]
  )(unlift(EnvVar.unapply))
   
  implicit val envVarReads: Reads[EnvVar] = (
    (JsPath \ "name").read[String] and 
    JsPath.read[EnvVar.Value]
  )(EnvVar.apply _)
  
  implicit val envVarFormat = Format(envVarReads, envVarWrites)
    
  implicit val quantityFormat = new Format[Resource.Quantity] {
     // Note: validate on read in future?
     def reads(json: JsValue) = 
        Json.fromJson[String](json).flatMap { s => JsSuccess(Resource.Quantity(s)) }
     def writes(o: Resource.Quantity) = Json.toJson(o.value)  
  }   
  
  implicit val resRqtsFormat: Format[Resource.Requirements] = (
    (JsPath \ "limits").formatMaybeEmptyMap[Resource.Quantity] and
    (JsPath \ "requests").formatMaybeEmptyMap[Resource.Quantity]
  )(Resource.Requirements.apply _, unlift(Resource.Requirements.unapply))
   
  implicit val protocolFmt = Format(enumReads(Protocol, Protocol.TCP), enumWrites)
  
  implicit val formatCntrProt: Format[Container.Port] = (
    (JsPath \ "containerPort").format[Int] and
    (JsPath \ "protocol").formatEnum(Protocol,Some(Protocol.TCP)) and
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "hostIP").formatMaybeEmptyString() and
    (JsPath \ "hostPort").formatNullable[Int]
  )(Container.Port.apply _, unlift(Container.Port.unapply))
  
  implicit val cntrStateWaitingFormat=Json.format[Container.Waiting]
  implicit val cntrStateRunningFormat=Json.format[Container.Running]
  implicit val cntrStateTerminatedFormat=Json.format[Container.Terminated]
  
  implicit val cntrStateReads: Reads[Container.State] = (
    (JsPath \ "waiting").read[Container.Waiting].map(x => x: Container.State) |
    (JsPath \ "running").read[Container.Running].map(x => x: Container.State) |
    (JsPath \ "terminated").read[Container.Terminated].map(x => x: Container.State) |
    Reads.pure(Container.Waiting()) // default
  )
  
  implicit val cntrStateWrites: Writes[Container.State] = Writes[Container.State] {
    state => state match {
      case wt: Container.Waiting => (JsPath \ "waiting").write[Container.Waiting](cntrStateWaitingFormat).writes(wt)
      case rn: Container.Running => (JsPath \ "running").write[Container.Running](cntrStateRunningFormat).writes(rn)
      case te: Container.Terminated => (JsPath \ "terminated").write[Container.Terminated](cntrStateTerminatedFormat).writes(te)
    }
  }
  
  implicit val cntrStatusFormat = Json.format[Container.Status]

  implicit val execActionFormat: Format[ExecAction] = (
    (JsPath \ "command").format[List[String]].inmap(cmd => ExecAction(cmd), (ea: ExecAction) => ea.command)
  )
  
  implicit val intOrStrReads: Reads[IntOrString] = (
    JsPath.read[Int].map(value => Left(value)) |
    JsPath.read[String].map(value => Right(value) )
  )
      
  implicit val intOrStrWrites = Writes[IntOrString] { 
     value => value match {
       case Left(i) => Writes.IntWrites.writes(i)
       case Right(s) => Writes.StringWrites.writes(s)
     }
  }
  
  implicit val intOrStringFormat: Format[IntOrString] = Format(intOrStrReads, intOrStrWrites)
  
  implicit val httpGetActionFormat: Format[HTTPGetAction] = (
      (JsPath \ "port").format[NameablePort] and
      (JsPath \ "host").formatMaybeEmptyString() and
      (JsPath \ "path").formatMaybeEmptyString() and 
      (JsPath \ "scheme").formatMaybeEmptyString() 
   )(HTTPGetAction.apply _, unlift(HTTPGetAction.unapply))
   
   
  implicit val tcpSocketActionFormat: Format[TCPSocketAction] = (
      (JsPath \ "port").format[NameablePort].inmap(port => TCPSocketAction(port), (tsa: TCPSocketAction) => tsa.port)
  )
      
   implicit val handlerReads: Reads[Handler] = (
      (JsPath \ "exec").read[ExecAction].map(x => x:Handler) |
      (JsPath \ "httpGet").read[HTTPGetAction].map(x => x:Handler) |
      (JsPath \ "tcpSocket").read[TCPSocketAction].map(x => x:Handler)
  )
  
  implicit val handlerWrites: Writes[Handler] = Writes[Handler] {
    handler => handler match {
      case ea: ExecAction => (JsPath \ "exec").write[ExecAction](execActionFormat).writes(ea)
      case hga: HTTPGetAction => (JsPath \ "httpGet").write[HTTPGetAction](httpGetActionFormat).writes(hga)
      case tsa: TCPSocketAction => (JsPath \ "tcpSocket").write[TCPSocketAction](tcpSocketActionFormat).writes(tsa)
    }  
  }
  implicit val handlerFormat: Format[Handler] = Format(handlerReads, handlerWrites)
  
  implicit val probeFormat : Format[Probe] = (
    JsPath.format[Handler] and
    (JsPath \ "initialDelaySeconds").formatMaybeEmptyInt() and
    (JsPath \ "timeoutSeconds").formatMaybeEmptyInt()
  )(Probe.apply _, unlift(Probe.unapply))
  
  
  implicit val lifecycleFormat: Format[Lifecycle] = Json.format[Lifecycle]
  
  import Volume._
  
  implicit val emptyDirReads: Reads[EmptyDir] = {
      (JsPath \ "medium").readNullable[String].map(
          medium => medium match {
            case Some(med) if (med=="Memory") => EmptyDir(MemoryStorageMedium)
            case _ => EmptyDir(DefaultStorageMedium)
          })      
  }  
  implicit val emptyDirWrites: Writes[EmptyDir] = Writes[EmptyDir] {
    ed => ed.medium match {
      case DefaultStorageMedium => (JsPath \ "medium").write[String].writes("")
      case MemoryStorageMedium => (JsPath \ "medium").write[String].writes("Memory")
    }
  }  
  implicit val emptyDirFormat: Format[EmptyDir] = Format(emptyDirReads, emptyDirWrites)
  
  implicit val hostPathFormat = Json.format[HostPath]  
  implicit val secretFormat = Json.format[Secret]
  implicit val gitFormat = Json.format[GitRepo]
  implicit val keyToPathFormat = Json.format[KeyToPath]

  implicit val configMapFormat: Format[ConfigMapVolumeSource] = (
      (JsPath \ "defaultMode").formatMaybeEmptyInt() and
      (JsPath \ "name").format[String] and
      (JsPath \ "items").formatMaybeEmptyList[KeyToPath]
    )(ConfigMapVolumeSource.apply _, unlift(ConfigMapVolumeSource.unapply))

  implicit  val gceFormat: Format[GCEPersistentDisk] = (
     (JsPath \ "pdName").format[String] and
     (JsPath \ "fsType").format[String] and 
     (JsPath \ "partition").formatMaybeEmptyInt() and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(GCEPersistentDisk.apply _, unlift(GCEPersistentDisk.unapply))
   
   implicit val awsFormat: Format[AWSElasticBlockStore] = (
     (JsPath \ "volumeID").format[String] and
     (JsPath \ "fsType").format[String] and 
     (JsPath \ "partition").formatMaybeEmptyInt() and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(AWSElasticBlockStore.apply _, unlift(AWSElasticBlockStore.unapply))
   
    implicit val nfsFormat: Format[NFS] = (
     (JsPath \ "server").format[String] and
     (JsPath \ "path").format[String] and 
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(NFS.apply _, unlift(NFS.unapply))
   
   implicit val glusterfsFormat: Format[Glusterfs] = (
     (JsPath \ "endpoints").format[String] and
     (JsPath \ "path").format[String] and 
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(Glusterfs.apply _, unlift(Glusterfs.unapply))
   
   implicit val rbdFormat: Format[RBD] = (
     (JsPath \ "monitors").format[List[String]] and
     (JsPath \ "image").format[String] and 
     (JsPath \ "fsType").format[String] and 
     (JsPath \ "pool").formatMaybeEmptyString() and 
     (JsPath \ "user").formatMaybeEmptyString() and
     (JsPath \ "keyring").formatMaybeEmptyString() and
     (JsPath \ "secretRef").formatNullable[LocalObjectReference] and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(RBD.apply _, unlift(RBD.unapply))
   
   implicit val iscsiFormat: Format[ISCSI] = (
     (JsPath \ "targetPortal").format[String] and 
     (JsPath \ "iqn").format[String] and 
     (JsPath \ "lun").format[Int] and 
     (JsPath \ "fsType").format[String] and 
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(ISCSI.apply _, unlift(ISCSI.unapply))
     
   implicit val persistentVolumeClaimRefFormat: Format[Volume.PersistentVolumeClaimRef] = (
     (JsPath \ "claimName").format[String] and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(Volume.PersistentVolumeClaimRef.apply _, unlift(Volume.PersistentVolumeClaimRef.unapply))
   
   implicit val persVolumeSourceReads: Reads[PersistentSource] = (
     (JsPath \ "hostPath").read[HostPath].map(x => x: PersistentSource) |
     (JsPath \ "gcePersistentDisk").read[GCEPersistentDisk].map(x => x:PersistentSource) |
     (JsPath \ "awsElasticBlockStore").read[AWSElasticBlockStore].map(x => x: PersistentSource) |
     (JsPath \ "nfs").read[NFS].map(x => x: PersistentSource) |
     (JsPath \ "glusterfs").read[Glusterfs].map(x => x: PersistentSource) |
     (JsPath \ "rbd").read[RBD].map(x => x: PersistentSource) |
     (JsPath \ "iscsi").read[ISCSI].map(x => x: PersistentSource) 
   )
   
   implicit val volumeSourceReads: Reads[Source] = (
     (JsPath \ "emptyDir").read[EmptyDir].map(x => x: Source) |
     (JsPath \ "secret").read[Secret].map(x => x:Source) |
       (JsPath \ "configMap").read[ConfigMapVolumeSource].map(x => x:Source) |
     (JsPath \ "gitRepo").read[GitRepo].map(x => x:Source) |
     (JsPath \ "persistentVolumeClaim").read[Volume.PersistentVolumeClaimRef].map(x => x: Source) |
     persVolumeSourceReads.map(x => x: Source)
   )
   
   implicit val persVolumeSourceWrites: Writes[PersistentSource] = Writes[PersistentSource] {
     case hp: HostPath => (JsPath \ "hostPath").write[HostPath](hostPathFormat).writes(hp)
     case gced: GCEPersistentDisk => (JsPath \ "gcePersistentDisk").write[GCEPersistentDisk](gceFormat).writes(gced)
     case awse: AWSElasticBlockStore => (JsPath \ "awsElasticBlockStore").write[AWSElasticBlockStore](awsFormat).writes(awse)
     case nfs: NFS => (JsPath \ "nfs").write[NFS](nfsFormat).writes(nfs)
     case gfs: Glusterfs => (JsPath \ "glusterfs").write[Glusterfs](glusterfsFormat).writes(gfs)
     case rbd: RBD => (JsPath \ "rbd").write[RBD](rbdFormat).writes(rbd) 
     case iscsi: ISCSI => (JsPath \ "iscsi").write[ISCSI](iscsiFormat).writes(iscsi) 
   }
  
   implicit val volumeSourceWrites: Writes[Source] = Writes[Source] { 
     source => source match {
       case ps:PersistentSource => persVolumeSourceWrites.writes(ps)
       case ed: EmptyDir => (JsPath \ "emptyDir").write[EmptyDir](emptyDirFormat).writes(ed)
       case secr: Secret => (JsPath \ "secret").write[Secret](secretFormat).writes(secr) 
       case cfgMp: ConfigMapVolumeSource => (JsPath \ "configMap").write[ConfigMapVolumeSource](configMapFormat).writes(cfgMp)
       case gitr: GitRepo => (JsPath \ "gitRepo").write[GitRepo](gitFormat).writes(gitr)
       case pvc: Volume.PersistentVolumeClaimRef => (JsPath \ "persistentVolumeClaim").write[Volume.PersistentVolumeClaimRef](persistentVolumeClaimRefFormat).writes(pvc) 
     }
   }
  
   implicit val volumeReads: Reads[Volume] = (
     (JsPath \ "name").read[String] and
     volumeSourceReads
   )(Volume.apply _)
   
        
   implicit val volumeWrites: Writes[Volume] = (
     (JsPath \ "name").write[String] and 
     JsPath.write[Source]
   )(unlift(Volume.unapply))
   
   implicit val volumeFormat: Format[Volume] = Format(volumeReads, volumeWrites)
   
   implicit val persVolSourceFormat: Format[PersistentSource] = Format(persVolumeSourceReads, persVolumeSourceWrites)
   
   implicit val volMountFormat: Format[Volume.Mount] = (
     (JsPath \ "name").format[String] and
     (JsPath \ "mountPath").format[String] and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean() and
     (JsPath \ "subPath").formatMaybeEmptyString()
   )(Volume.Mount.apply _, unlift(Volume.Mount.unapply))
  
   implicit val pullPolicyFormat: Format[Container.PullPolicy.Value] = 
       Format(enumReads(Container.PullPolicy, Container.PullPolicy.IfNotPresent), enumWrites)
       
  implicit val containerFormat: Format[Container] = (
    (JsPath \ "name").format[String] and
    (JsPath \ "image").format[String] and
    (JsPath \ "command").formatMaybeEmptyList[String] and
    (JsPath \ "args").formatMaybeEmptyList[String] and
    (JsPath \ "workingDir").formatNullable[String] and
    (JsPath \ "ports").formatMaybeEmptyList[Container.Port] and
    (JsPath \ "env").formatMaybeEmptyList[EnvVar] and
    (JsPath \ "resources").formatNullable[Resource.Requirements] and
    (JsPath \ "volumeMounts").formatMaybeEmptyList[Volume.Mount] and
    (JsPath \ "livenessProbe").formatNullable[Probe] and
    (JsPath \ "readinessProbe").formatNullable[Probe] and
    (JsPath \ "lifecycle").formatNullable[Lifecycle] and
    (JsPath \ "terminationMessagePath").formatMaybeEmptyString() and
    (JsPath \ "imagePullPolicy").formatEnum(Container.PullPolicy, Some(Container.PullPolicy.IfNotPresent)) and
    (JsPath \ "securityContext").formatNullable[Security.Context]
  )(Container.apply _, unlift(Container.unapply))
   
  implicit val podStatusCondFormat : Format[Pod.Condition] = (
      (JsPath \ "type").format[String] and
      (JsPath \ "status").format[String]
    )(Pod.Condition.apply _, unlift(Pod.Condition.unapply))
     
  
  implicit val podStatusFormat: Format[Pod.Status] = (
      (JsPath \ "phase").formatNullableEnum(Pod.Phase) and
      (JsPath \ "conditions").formatMaybeEmptyList[Pod.Condition] and
      (JsPath \ "message").formatNullable[String] and
      (JsPath \ "reason").formatNullable[String] and
      (JsPath \ "hostIP").formatNullable[String] and
      (JsPath \ "podIP").formatNullable[String] and
      (JsPath \ "startTime").formatNullable[Timestamp] and
      (JsPath \ "containerStatuses").formatMaybeEmptyList[Container.Status]
    )(Pod.Status.apply _, unlift(Pod.Status.unapply))
  
  implicit lazy val podFormat : Format[Pod] = (
      objFormat and
      (JsPath \ "spec").formatNullable[Pod.Spec] and
      (JsPath \ "status").formatNullable[Pod.Status]
    ) (Pod.apply _, unlift(Pod.unapply))  
    
  
  implicit val podSpecFormat: Format[Pod.Spec] = (
      (JsPath \ "containers").format[List[Container]] and
      (JsPath \ "volumes").formatMaybeEmptyList[Volume] and
      (JsPath \ "restartPolicy").formatEnum(RestartPolicy, Some(RestartPolicy.Always)) and
      (JsPath \ "terminationGracePeriodSeconds").formatNullable[Int] and
      (JsPath \ "activeDeadlineSeconds").formatNullable[Int] and
      (JsPath \ "dnsPolicy").formatEnum(DNSPolicy, Some(DNSPolicy.Default)) and
      (JsPath \ "nodeSelector").formatMaybeEmptyMap[String] and
      (JsPath \ "serviceAccountName").formatMaybeEmptyString() and
      (JsPath \ "nodeName").formatMaybeEmptyString() and
      (JsPath \ "hostNetwork").formatMaybeEmptyBoolean() and
      (JsPath \ "imagePullSecrets").formatMaybeEmptyList[LocalObjectReference]
    )(Pod.Spec.apply _, unlift(Pod.Spec.unapply))
    
  implicit val podTemplSpecFormat: Format[Pod.Template.Spec] = Json.format[Pod.Template.Spec]
  implicit lazy val podTemplFormat : Format[Pod.Template] = (
      objFormat and
      (JsPath \ "spec").formatNullable[Pod.Template.Spec]
    ) (Pod.Template.apply _, unlift(Pod.Template.unapply))      
  
  implicit val repCtrlrSpecFormat = Json.format[ReplicationController.Spec]
  implicit val repCtrlrStatusFormat = Json.format[ReplicationController.Status]
  
   implicit lazy val repCtrlrFormat: Format[ReplicationController] = (
    objFormat and
    (JsPath \ "spec").formatNullable[ReplicationController.Spec] and
    (JsPath \ "status").formatNullable[ReplicationController.Status]
  ) (ReplicationController.apply _, unlift(ReplicationController.unapply))
  
   
  implicit val loadBalIngressFmt: Format[Service.LoadBalancer.Ingress] = Json.format[Service.LoadBalancer.Ingress]
  implicit val loadBalStatusFmt: Format[Service.LoadBalancer.Status] =
    (JsPath \ "ingress").formatMaybeEmptyList[Service.LoadBalancer.Ingress].
        inmap(ingress => Service.LoadBalancer.Status(ingress), (lbs:Service.LoadBalancer.Status) => lbs.ingress)
        
  implicit val serviceStatusFmt: Format[Service.Status] = 
    (JsPath \ "loadBalancer").formatNullable[Service.LoadBalancer.Status].
        inmap(lbs=> Service.Status(lbs), (ss:Service.Status) => ss.loadBalancer)  
   
  implicit val servicePortFmt: Format[Service.Port] = (
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "protocol").formatEnum(Protocol, Some(Protocol.TCP)) and
    (JsPath \ "port").format[Int] and
    (JsPath \ "targetPort").formatNullable[NameablePort] and
    (JsPath \ "nodePort").formatMaybeEmptyInt()
  ) (Service.Port.apply _, unlift(Service.Port.unapply))
  
  implicit val serviceSpecFmt: Format[Service.Spec] = (
      (JsPath \ "ports").format[List[Service.Port]] and
      (JsPath \ "selector").formatMaybeEmptyMap[String] and
      (JsPath \ "clusterIP").formatMaybeEmptyString() and
      (JsPath \ "type").formatEnum(Service.Type, Some(Service.Type.ClusterIP)) and
      (JsPath \ "externalIPs").formatMaybeEmptyList[String] and
      (JsPath \ "sessionAffinity").formatEnum(Service.Affinity, Some(Service.Affinity.None))
   )(Service.Spec.apply _, unlift(Service.Spec.unapply))  
   
  implicit val serviceFmt: Format[Service] = (
     objFormat and
     (JsPath \ "spec").formatNullable[Service.Spec] and
     (JsPath \ "status").formatNullable[Service.Status]
  )(Service.apply _, unlift(Service.unapply))
  
  implicit val endpointsAddressFmt: Format[Endpoints.Address] = Json.format[Endpoints.Address]
  implicit val endpointPortFmt: Format[Endpoints.Port] = Json.format[Endpoints.Port]
  implicit val endpointSubsetFmt: Format[Endpoints.Subset] = Json.format[Endpoints.Subset]
  
  implicit val endpointFmt: Format[Endpoints] = (
    objFormat and
    (JsPath \ "subsets").format[List[Endpoints.Subset]]
  )(Endpoints.apply _, unlift(Endpoints.unapply))
  
  implicit val nodeSysInfoFmt: Format[Node.SystemInfo] = Json.format[Node.SystemInfo]
   
  implicit val nodeAddrFmt: Format[Node.Address] = (
    (JsPath \ "type").format[String] and
    (JsPath \ "address").format[String]
  )(Node.Address.apply _, unlift(Node.Address.unapply))
 
  implicit val nodeCondFmt: Format[Node.Condition] = (
    (JsPath \ "type").format[String] and
    (JsPath \ "status").format[String] and
    (JsPath \ "lastHeartbeatTime").formatNullable[Timestamp] and
    (JsPath \ "lastTransitionTime").formatNullable[Timestamp] and
    (JsPath \ "reason").formatNullable[String] and
    (JsPath \ "message").formatNullable[String]
  )(Node.Condition.apply _, unlift(Node.Condition.unapply))
  
  implicit val nodeStatusFmt: Format[Node.Status] = (
    (JsPath \ "capacity").formatMaybeEmptyMap[Resource.Quantity] and
    (JsPath \ "phase").formatNullableEnum(Node.Phase) and
    (JsPath \ "conditions").formatMaybeEmptyList[Node.Condition] and
    (JsPath \ "addresses").formatMaybeEmptyList[Node.Address] and
    (JsPath \ "nodeInfo").formatNullable[Node.SystemInfo]
  )(Node.Status.apply _, unlift(Node.Status.unapply)) 
   
  implicit val nodeSpecFmt: Format[Node.Spec] =(
    (JsPath \ "podCIDR").formatMaybeEmptyString() and
    (JsPath \ "providerID").formatMaybeEmptyString() and
    (JsPath \ "unschedulable").formatMaybeEmptyBoolean() and
    (JsPath \ "externalID").formatMaybeEmptyString()
  )(Node.Spec.apply _, unlift(Node.Spec.unapply))
 
  implicit val nodeFmt: Format[Node] = (
    objFormat and
    (JsPath \ "spec").formatNullable[Node.Spec] and
    (JsPath \ "status").formatNullable[Node.Status]
  )(Node.apply _, unlift(Node.unapply))
  
  implicit val eventSrcFmt: Format[Event.Source] = Json.format[Event.Source]
  implicit val eventFmt: Format[Event] = Json.format[Event]
   
  implicit val accessModeFmt: Format[PersistentVolume.AccessMode.AccessMode] = Format(enumReads(PersistentVolume.AccessMode), enumWrites)
  implicit val pvolPhaseFmt: Format[PersistentVolume.Phase.Phase] = Format(enumReads(PersistentVolume.Phase), enumWrites)
  implicit val reclaimPolicyFmt: Format[PersistentVolume.ReclaimPolicy.ReclaimPolicy] = Format(enumReads(PersistentVolume.ReclaimPolicy), enumWrites)
    
  implicit val perVolSpecFmt: Format[PersistentVolume.Spec] = (
      (JsPath \ "capacity").formatMaybeEmptyMap[Resource.Quantity] and
      JsPath.format[PersistentSource] and
      (JsPath \ "accessModes").formatMaybeEmptyList[PersistentVolume.AccessMode.AccessMode] and
      (JsPath \ "claimRef").formatNullable[ObjectReference] and
      (JsPath \ "persistentVolumeReclaimPolicy").formatNullableEnum(PersistentVolume.ReclaimPolicy)
  )(PersistentVolume.Spec.apply _, unlift(PersistentVolume.Spec.unapply))
    
  implicit val persVolStatusFmt: Format[PersistentVolume.Status] = (
      (JsPath \ "phase").formatNullableEnum(PersistentVolume.Phase) and
      (JsPath \ "accessModes").formatMaybeEmptyList[PersistentVolume.AccessMode.AccessMode]
  )(PersistentVolume.Status.apply _, unlift(PersistentVolume.Status.unapply))
      
  implicit val persVolFmt: Format[PersistentVolume] = (
    objFormat and
    (JsPath \ "spec").formatNullable[PersistentVolume.Spec] and
    (JsPath \ "status").formatNullable[PersistentVolume.Status]
  )(PersistentVolume.apply _, unlift(PersistentVolume.unapply))
  
  implicit val pvClaimSpecFmt: Format[PersistentVolumeClaim.Spec] = (
    (JsPath \ "accessModes").formatMaybeEmptyList[PersistentVolume.AccessMode.AccessMode] and
    (JsPath \ "resources").formatNullable[Resource.Requirements] and
    (JsPath \ "volumeName").formatMaybeEmptyString()
  )(PersistentVolumeClaim.Spec.apply _, unlift(PersistentVolumeClaim.Spec.unapply))
  
  implicit val pvClaimStatusFmt: Format[PersistentVolumeClaim.Status] = (
    (JsPath \ "phase").formatNullableEnum(PersistentVolume.Phase) and
    (JsPath \ "accessModes").formatMaybeEmptyList[PersistentVolume.AccessMode.AccessMode]
  )(PersistentVolumeClaim.Status.apply _, unlift(PersistentVolumeClaim.Status.unapply))
  
  implicit val pvClaimFmt: Format[PersistentVolumeClaim] = (
    objFormat and
    (JsPath \ "spec").formatNullable[PersistentVolumeClaim.Spec] and
    (JsPath \ "status").formatNullable[PersistentVolumeClaim.Status]
  )(PersistentVolumeClaim.apply _, unlift(PersistentVolumeClaim.unapply))

  implicit val configMapFmt: Format[ConfigMap] = Json.format[ConfigMap]

  implicit val svcAccountFmt: Format[ServiceAccount] = (
    objFormat and
    (JsPath \ "secrets").formatMaybeEmptyList[ObjectReference] and
    (JsPath \ "imagePullSecrets").formatMaybeEmptyList[LocalObjectReference]
  )(ServiceAccount.apply _, unlift(ServiceAccount.unapply))
  
  implicit val base64Format: Format[Array[Byte]] = (
      JsPath.format[String].inmap(s => Base64.decodeBase64(s), (bytes: Array[Byte]) => Base64.encodeBase64String(bytes))
  )

  implicit val mapStringByteArrayFormat: Format[Map[String,Array[Byte]]] =
    JsPath.format[Map[String,String]]
      .inmap(_.map({case (k,v) => k -> Base64.decodeBase64(v.getBytes)}),
        (map: Map[String, Array[Byte]]) => map.map({case (k,v) => k -> Base64.encodeBase64String(v)}))

  import skuber.Secret
  implicit val secretFmt: Format[Secret] = (
    objFormat and
    (JsPath \ "data").formatMaybeEmptyByteArrayMap and
    (JsPath \ "type").formatMaybeEmptyString()
  )(Secret.apply _, unlift(Secret.unapply))
  
  implicit val limitRangeItemTypeFmt: Format[LimitRange.ItemType.Type] = enumFormat(LimitRange.ItemType) 
        
  implicit val limitRangeItemFmt: Format[LimitRange.Item] = (
     (JsPath \ "type").formatNullableEnum(LimitRange.ItemType) and
     (JsPath \ "max").formatMaybeEmptyMap[Resource.Quantity] and
     (JsPath \ "min").formatMaybeEmptyMap[Resource.Quantity] and
     (JsPath \ "default").formatMaybeEmptyMap[Resource.Quantity] and
     (JsPath \ "defaultRequirements").formatMaybeEmptyMap[Resource.Quantity] and
     (JsPath \ "maxLimitRequestRatio").formatMaybeEmptyMap[Resource.Quantity]
  )(LimitRange.Item.apply _, unlift(LimitRange.Item.unapply))
   
  implicit val limitRangeSpecFmt: Format[LimitRange.Spec] = 
     (JsPath \ "items").formatMaybeEmptyList[LimitRange.Item].inmap(
         items => LimitRange.Spec(items), (spec: LimitRange.Spec) => spec.items)
   
  implicit val limitRangeFmt: Format[LimitRange] = (
     objFormat and
     (JsPath \ "spec").formatNullable[LimitRange.Spec]
  )(LimitRange.apply _, unlift(LimitRange.unapply))
  
  implicit val resourceQuotaSpecFmt: Format[Resource.Quota.Spec] =
    (JsPath \ "hard").formatMaybeEmptyMap[Resource.Quantity].inmap(
        hard => Resource.Quota.Spec(hard),(spec: Resource.Quota.Spec) => spec.hard)
  
  implicit val resouceQuotaStatusFmt: Format[Resource.Quota.Status] = (
    (JsPath \ "hard").formatMaybeEmptyMap[Resource.Quantity] and
    (JsPath \ "used").formatMaybeEmptyMap[Resource.Quantity]
  )(Resource.Quota.Status.apply _,unlift(Resource.Quota.Status.unapply))
  
  implicit val resourceQuotaFmt: Format[Resource.Quota] = (
    objFormat and  
    (JsPath \ "spec").formatNullable[Resource.Quota.Spec] and
    (JsPath \ "status").formatNullable[Resource.Quota.Status]
  )(Resource.Quota.apply _,unlift(Resource.Quota.unapply))
      
  implicit val podListFmt: Format[PodList] = KListFormat[Pod].apply(PodList.apply _,unlift(PodList.unapply))
  implicit val nodeListFmt: Format[NodeList] = KListFormat[Node].apply(NodeList.apply _,unlift(NodeList.unapply))
  implicit val serviceListFmt: Format[ServiceList] = KListFormat[Service].apply(ServiceList.apply _,unlift(ServiceList.unapply))
  implicit val endpointListFmt: Format[EndpointList] = KListFormat[Endpoints].apply(EndpointList.apply _,unlift(EndpointList.unapply))
  implicit val eventListFmt: Format[EventList] = KListFormat[Event].apply(EventList.apply _,unlift(EventList.unapply))
  implicit val namespaceListFmt: Format[NamespaceList] = KListFormat[Namespace].
                    apply(NamespaceList.apply _,unlift(NamespaceList.unapply))
  implicit val replCtrlListFmt: Format[ReplicationControllerList] = KListFormat[ReplicationController].
                    apply(ReplicationControllerList.apply _,unlift(ReplicationControllerList.unapply))
  implicit val persVolListFmt: Format[PersistentVolumeList] = KListFormat[PersistentVolume].  
                    apply(PersistentVolumeList.apply _,unlift(PersistentVolumeList.unapply))
  implicit val persVolClaimListFmt: Format[PersistentVolumeClaimList] = KListFormat[PersistentVolumeClaim].  
                    apply(PersistentVolumeClaimList.apply _,unlift(PersistentVolumeClaimList.unapply))
  implicit val svcAcctListFmt: Format[ServiceAccountList] = KListFormat[ServiceAccount].  
                    apply(ServiceAccountList.apply _,unlift(ServiceAccountList.unapply))
  implicit val resQuotaListFmt: Format[ResourceQuotaList] = KListFormat[Resource.Quota].
                    apply(ResourceQuotaList.apply _,unlift(ResourceQuotaList.unapply))
  implicit val secretListFmt: Format[SecretList] = KListFormat[Secret].  
                    apply(SecretList.apply _,unlift(SecretList.unapply))
  implicit val limitRangeListFmt: Format[LimitRangeList] = KListFormat[LimitRange].  
                    apply(LimitRangeList.apply _,unlift(LimitRangeList.unapply))

  case class SelMatchExpression(
    key: String,
    operator: String = "Exists",
    values: Option[List[String]] = None
  )
  implicit val selMatchExpressionFmt = Json.format[SelMatchExpression]
  case class OnTheWireSelector(matchLabels: Option[Map[String,String]]=None, matchExpressions: Option[List[SelMatchExpression]]=None)

  import LabelSelector._
  private def labelSelMatchExprToRequirement(expr: SelMatchExpression): LabelSelector.Requirement = {
    expr.operator match {
      case "Exists" => ExistsRequirement(expr.key)
      case "DoesNotExist" => NotExistsRequirement(expr.key)
      case "In" => InRequirement(expr.key,expr.values.get)
      case "NotIn" => NotInRequirement(expr.key, expr.values.get)
      case other => throw new Exception("unknown label selector expression operator: " + other)
    }
  }

  private def otwSelectorToLabelSelector(otws: OnTheWireSelector): LabelSelector = {
      val equalityBasedReqsOpt: Option[List[IsEqualRequirement]] = otws.matchLabels.map { labelKVMap =>
        labelKVMap.map(kv => IsEqualRequirement(kv._1, kv._2)).toList
      }
      val setBasedReqsOpt: Option[List[Requirement]] = otws.matchExpressions.map { expressions =>
        expressions map labelSelMatchExprToRequirement
        }
      val ebReqs=equalityBasedReqsOpt.getOrElse(List())
      val sbReqs=setBasedReqsOpt.getOrElse(List())
      val allReqs = ebReqs ++ sbReqs
      LabelSelector(allReqs: _*)
  }

  private def labelSelToOtwSelector(sel: LabelSelector): OnTheWireSelector = {
    val eqBased = sel.requirements.collect {
      case IsEqualRequirement(key,value) => (key,value)
    }.toMap
    val matchLabels=if (eqBased.isEmpty) None else Some(eqBased)

    val setBased = sel.requirements.collect {
      case ExistsRequirement(key) => SelMatchExpression(key)
      case NotExistsRequirement(key) => SelMatchExpression(key, "DoesNotExist")
      case IsNotEqualRequirement(key,value) => SelMatchExpression(key, "NotIn", Some(List(value)))
      case InRequirement(key,values) =>  SelMatchExpression(key, "in", Some(values))
      case NotInRequirement(key,values) =>  SelMatchExpression(key, "NotIn", Some(values))
    }.toList
    val matchExpressions = if(setBased.isEmpty) None else Some(setBased)

    OnTheWireSelector(matchLabels = matchLabels, matchExpressions = matchExpressions)
  }

  implicit val otwsFormat = Json.format[OnTheWireSelector]

  class LabelSelectorFormat(path: JsPath) {
    def formatNullableLabelSelector: OFormat[Option[LabelSelector]] =
      path.formatNullable[OnTheWireSelector].inmap[Option[LabelSelector]](
        _.map(otwSelectorToLabelSelector),
        selOpt => selOpt.map(labelSelToOtwSelector)
      )
  }

  implicit def jsPath2LabelSelFormat(path: JsPath) = new LabelSelectorFormat(path)

  // formatters for API 'supporting' types i.e. non resource types such as status and watch events 
  object apiobj {
     
    import skuber.api.client._
  
    // this handler reads a generic Status response from the server                                    
    implicit val statusReads: Reads[Status] = (
      (JsPath \ "apiVersion").read[String] and
      (JsPath \ "kind").read[String] and
      (JsPath \ "metadata").read[ListMeta] and
      (JsPath \ "status").readNullable[String] and
      (JsPath \ "message").readNullable[String] and
      (JsPath \ "reason").readNullable[String] and
      (JsPath \ "details").readNullable[JsValue].map(ov => ov.map( x => x:Any)) and
      (JsPath \ "code").readNullable[Int]
    )(Status.apply _)      
   
    implicit val deleteOptionsWrite: Writes[DeleteOptions] = (
      (JsPath \ "apiVersion").formatMaybeEmptyString() and 
      (JsPath \ "kind").formatMaybeEmptyString() and 
      (JsPath \ "gracePeriodSeconds").formatMaybeEmptyInt()
    )(DeleteOptions.apply _, unlift(DeleteOptions.unapply))     
    
    def watchEventFormat[T <: ObjectResource](implicit objfmt: Format[T]) : Format[WatchEvent[T]] = (
      (JsPath \ "type").formatEnum(EventType) and
      (JsPath \ "object").format[T]
    )(WatchEvent.apply[T] _, unlift(WatchEvent.unapply[T]))
    
  }  
}

