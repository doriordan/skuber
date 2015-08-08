package skuber.api

import java.time._
import java.time.format._

import skuber.model._
import skuber.model.Model._
import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError


/**
 * @author David O'Riordan
 * Play json formatters for Kubernetes types
 */
object JsonReadWrite {
  
  // Formatters for the Java 8 ZonedDateTime objects that represent
  // (ISO 8601 / RFC 3329 compatible) Kubernetes timestamp fields 
  implicit val timewWrites = Writes.temporalWrites[ZonedDateTime, DateTimeFormatter](
      DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  implicit val timeReads = Reads.DefaultZonedDateTimeReads    
      
  // Per Kubernetes rules there are many fields which can have an "empty" value (e.g. an empty string, 
  // list or map; int value 0 or boolean value false). These can be omitted (and we expect Kubernetes to omit them) 
  // from the Json. 
  // In some of these cases in this Scala API, mainly to simplify client code the type of the field is the 
  // direct type (i.e. no Option wrapping or unwrapping required) - for these we use the customer formatters below 
  // to handle the omitted/empty case properly.
  // (Note - for other of these cases this API uses Option types - mainly fields likely to be read-only for
  // most clients e.g Status fields. In these cases the ability to use Option monadic methods for safe processing 
  // of the (possibly omitted) data received from Kubernetes is deemed to outweigh the overhead of wrapping/unwrapping
  // Option types. For these cases the standard formatNullable method is used for json formatting.
  // Other "optional/nillable" fields are of object types that are represented as case classes in this API - these
  // fields are always Option types).
  
  class MaybeEmpty(val path: JsPath) {
    def formatMaybeEmptyString(omitEmpty: Boolean=true): OFormat[String] =
      path.formatNullable[String].inmap[String](_.getOrElse(emptyS), s => if (omitEmpty && s.isEmpty) None else Some(s) )
      
    def formatMaybeEmptyList[T](implicit tReads: Reads[T], tWrites: Writes[T], omitEmpty: Boolean=true) : OFormat[List[T]] =
      path.formatNullable[List[T]].inmap[List[T]](_.getOrElse(emptyL[T]), l => if (omitEmpty && l.isEmpty) None else Some(l))
      
     def formatMaybeEmptyMap[V](
         implicit  vReads: Reads[V],  vWrites: Writes[V],
         omitEmpty: Boolean=true) : OFormat[Map[String,V]] =
      path.formatNullable[Map[String,V]].inmap[Map[String,V]](_.getOrElse(emptyM[V]), m => if (omitEmpty && m.isEmpty) None else Some(m))
        
    // Boolean: the empty value is 'false'  
    def formatMaybeEmptyBoolean(omitEmpty: Boolean=true) : OFormat[Boolean] =
      path.formatNullable[Boolean].inmap[Boolean](_.getOrElse(false), b => if (omitEmpty && !b) None else Some(b))
      
    // Int: the empty value is 0
    def formatMaybeEmptyInt(omitEmpty: Boolean=true) : OFormat[Int] =
      path.formatNullable[Int].inmap[Int](_.getOrElse(0), i => if (omitEmpty && i==0) None else Some(i))
  }
  // we make the above formatter methods available on JsPath objects via this implicit conversion
  implicit def jsPath2MaybeEmpty(path: JsPath) = new MaybeEmpty(path)
   
  // general formatting for Enumerations - from https://gist.github.com/mikesname/5237809
  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
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

  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = Format(enumReads(enum), enumWrites)
   
  // formatting of the Kubernetes types
  
  implicit lazy val objFormat =
    (JsPath \ "kind").format[String] and
    (JsPath \ "apiVersion").format[String] and
    (JsPath \ "metadata").lazyFormat[ObjectMeta](objectMetaFormat) 
   // matadata format must be lazy as it can be used in indirectly recursive namespace structure (Namespace has a metadata.namespace field)
    
  implicit lazy val objectMetaFormat: Format[ObjectMeta] = (
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "generateName").formatMaybeEmptyString() and 
    (JsPath \ "namespace").formatMaybeEmptyString() and
    (JsPath \ "uid").formatMaybeEmptyString() and
    (JsPath \ "selfLink").formatMaybeEmptyString() and
    (JsPath \ "resourceVersion").formatMaybeEmptyString() and
    (JsPath \ "creationTimestamp").formatNullable[Timestamp] and
    (JsPath \ "deletionTimestamp").formatNullable[Timestamp] and
    (JsPath \ "labels").formatNullable[Map[String, String]] and
    (JsPath \ "annotations").formatNullable[Map[String, String]]
  )(ObjectMeta.apply _, unlift(ObjectMeta.unapply))
    
  implicit val localObjRefFormat = Json.format[LocalObjectReference]
  
  
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
 
  implicit val envVarFldSel = Json.format[EnvVar.FieldSelector]
  
  implicit val envVarValueWrite = Writes[EnvVar.Value] { 
     value => value match {
       case EnvVar.StringValue(str) => (JsPath \ "value").write[String].writes(str)
       case EnvVar.Source(fs) => (JsPath \ "valueFrom").write[EnvVar.FieldSelector](envVarFldSel).writes(fs)
     }
  }
  
  implicit val envVarValueReads: Reads[EnvVar.Value] = (
    (JsPath \ "value").read[String].map(value => EnvVar.StringValue(value)) |
    (JsPath \ "valueFrom").read[EnvVar.FieldSelector].map(value => EnvVar.Source(value) )
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
   
  implicit val formatCntrProt: Format[Container.Port] = (
    (JsPath \ "containerPort").format[Int] and
    (JsPath \ "protocol").formatMaybeEmptyString() and
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
    (JsPath \ "terminated").read[Container.Terminated].map(x => x: Container.State)
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
  
  
  implicit val servicePortReads: Reads[ServicePort] = (
    (JsPath \ "port").read[Int].map(value => Left(value)) |
    (JsPath \ "port").read[String].map(value => Right(value) )
  )
  
     
  implicit val servicePortWrite = Writes[ServicePort] { 
     value => value match {
       case Left(i) => (JsPath \ "port").write[Int].writes(i)
       case Right(s) => (JsPath \ "port").write[String].writes(s)
     }
  }
  
  implicit val servicePortFormat: Format[ServicePort] = Format(servicePortReads, servicePortWrite)
  
  implicit val httpGetActionFormat: Format[HTTPGetAction] = (
      JsPath.format[ServicePort] and
      (JsPath \ "host").formatMaybeEmptyString() and
      (JsPath \ "path").formatMaybeEmptyString() and 
      (JsPath \ "scheme").formatMaybeEmptyString() 
   )(HTTPGetAction.apply _, unlift(HTTPGetAction.unapply))
   
   
  implicit val tcpSocketActionFormat: Format[TCPSocketAction] = (
      (JsPath \ "port").format[ServicePort].inmap(port => TCPSocketAction(port), (tsa: TCPSocketAction) => tsa.port)
  )
      
   implicit val handlerReads: Reads[Handler] = (
      (JsPath \ "exec").read[ExecAction].map(x => x:Handler) |
      (JsPath \ "httpGet").read[HTTPGetAction].map(x => x:Handler) |
      (JsPath \ "tcpSocket").read[TCPSocketAction].map(x => x:Handler)
  )
  
  implicit val handlerWrites: Writes[Handler] = Writes[Handler] {
    handler => handler match {
      case ea: ExecAction => (JsPath \ "execAction").write[ExecAction](execActionFormat).writes(ea)
      case hga: HTTPGetAction => (JsPath \ "httpGet").write[HTTPGetAction](httpGetActionFormat).writes(hga)
      case tsa: TCPSocketAction => (JsPath \ "tcpSocketAction").write[TCPSocketAction](tcpSocketActionFormat).writes(tsa)
    }  
  }
  implicit val handlerFormat: Format[Handler] = Format(handlerReads, handlerWrites)
  
  implicit val probeFormat : Format[Probe] = (
    JsPath.format[Handler] and
    (JsPath \ "initialDelaySeconds").formatMaybeEmptyInt() and
    (JsPath \ "timeoutSeconds").formatMaybeEmptyInt()
  )(Probe.apply _, unlift(Probe.unapply))
  
  
  implicit val lifecycleFormat: Format[Lifecycle] = Json.format[Lifecycle]
  
  import skuber.model.Volume._
  
  implicit val emptyDirFormat = Json.format[EmptyDir]
   implicit val hostPathFormat = Json.format[HostPath]  
   implicit val secretFormat = Json.format[Secret]
   implicit val gitFormat = Json.format[GitRepo]
   
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
     (JsPath \ "server").format[String] and
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
   
     
   implicit val persistentVolumeClaimFormat: Format[PersistentVolumeClaim] = (
     (JsPath \ "claimName").format[String] and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(PersistentVolumeClaim.apply _, unlift(PersistentVolumeClaim.unapply))
   
   implicit val volumeSourceReads: Reads[Source] = (
     (JsPath \ "emptyDir").read[EmptyDir].map(x => x: Source) |
     (JsPath \ "hostPath").read[HostPath].map(x => x: Source) |
     (JsPath \ "secret").read[Secret].map(x => x:Source) |
     (JsPath \ "gitRepo").read[GitRepo].map(x => x:Source) |
     (JsPath \ "gcePersistentDisk").read[GCEPersistentDisk].map(x => x:Source) |
     (JsPath \ "awsElasticBlockStore").read[AWSElasticBlockStore].map(x => x: Source) |
     (JsPath \ "nfs").read[NFS].map(x => x: Source) |
     (JsPath \ "glusterfs").read[Glusterfs].map(x => x: Source) |
     (JsPath \ "rbd").read[RBD].map(x => x: Source) |
     (JsPath \ "iscsi").read[ISCSI].map(x => x: Source) |
     (JsPath \ "persistentVolumeClaim").read[PersistentVolumeClaim].map(x => x: Source)
   )
   
   implicit val volumeSourceWrites: Writes[Source] = Writes[Source] { 
     source => source match {
       case ed: EmptyDir => (JsPath \ "emptyDir").write[EmptyDir](emptyDirFormat).writes(ed)
       case hp: HostPath => (JsPath \ "hostPath").write[HostPath](hostPathFormat).writes(hp)
       case secr: Secret => (JsPath \ "secret").write[Secret](secretFormat).writes(secr) 
       case gitr: GitRepo => (JsPath \ "gitRepo").write[GitRepo](gitFormat).writes(gitr)
       case gced: GCEPersistentDisk => (JsPath \ "gcePersistentDisk").write[GCEPersistentDisk](gceFormat).writes(gced)
       case awse: AWSElasticBlockStore => (JsPath \ "awsElasticBlockStore").write[AWSElasticBlockStore](awsFormat).writes(awse)
       case nfs: NFS => (JsPath \ "nfs").write[NFS](nfsFormat).writes(nfs)
       case gfs: Glusterfs => (JsPath \ "glusterfs").write[Glusterfs](glusterfsFormat).writes(gfs)
       case rbd: RBD => (JsPath \ "rbd").write[RBD](rbdFormat).writes(rbd) 
       case iscsi: ISCSI => (JsPath \ "iscsi").write[ISCSI](iscsiFormat).writes(iscsi) 
       case pvc: PersistentVolumeClaim => (JsPath \ "persistentVolumeClaim").write[PersistentVolumeClaim](persistentVolumeClaimFormat).writes(pvc) 
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
   
   implicit val volMountFormat: Format[Volume.Mount] = (
     (JsPath \ "name").format[String] and
     (JsPath \ "mountPath").format[String] and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(Volume.Mount.apply _, unlift(Volume.Mount.unapply))
  
  implicit val containerFormat: Format[Container] = (
    (JsPath \ "name").format[String] and
    (JsPath \ "image").format[String] and
    (JsPath \ "command").formatMaybeEmptyList[String] and
    (JsPath \ "args").formatMaybeEmptyList[String] and
    (JsPath \ "workingDir").formatMaybeEmptyString() and
    (JsPath \ "ports").formatMaybeEmptyList[Container.Port] and
    (JsPath \ "env").formatMaybeEmptyList[EnvVar] and
    (JsPath \ "resources").formatNullable[Resource.Requirements] and
    (JsPath \ "volumeMounts").formatMaybeEmptyList[Volume.Mount] and
    (JsPath \ "livenessProbe").formatNullable[Probe] and
    (JsPath \ "readinessProbe").formatNullable[Probe] and
    (JsPath \ "lifeCycle").formatNullable[Lifecycle] and
    (JsPath \ "terminationMessagePath").formatMaybeEmptyString() and
    (JsPath \ "imagePullPolicy").formatMaybeEmptyString() and
    (JsPath \ "securityContext").formatNullable[Security.Context]
  )(Container.apply _, unlift(Container.unapply))
   
  implicit val podStatusCondFormat : Format[Pod.Condition] = (
      (JsPath \ "type").format[String] and
      (JsPath \ "status").format[String]
    )(Pod.Condition.apply _, unlift(Pod.Condition.unapply))
    
  implicit val podStatusFormat: Format[Pod.Status] = (
      (JsPath \ "phase").format[String] and
      (JsPath \ "conditions").formatMaybeEmptyList[Pod.Condition] and
      (JsPath \ "message").formatNullable[String] and
      (JsPath \ "reason").formatNullable[String] and
      (JsPath \ "hostIP").formatNullable[String] and
      (JsPath \ "podIP").formatNullable[String] and
      (JsPath \ "startTime").formatNullable[Timestamp] and
      (JsPath \ "containerStatuses").formatMaybeEmptyList[Container.Status]
    )(Pod.Status.apply _, unlift(Pod.Status.unapply))
    
  implicit val dnsPolicyFormat = enumFormat(DNSPolicy)  
  
  implicit lazy val podFormat : Format[Pod] = (
      objFormat and
      (JsPath \ "spec").formatNullable[Pod.Spec] and
      (JsPath \ "status").formatNullable[Pod.Status]
    ) (Pod.apply _, unlift(Pod.unapply))  
  
  implicit val podSpecFormat: Format[Pod.Spec] = (
      (JsPath \ "containers").format[List[Container]] and
      (JsPath \ "volumes").formatMaybeEmptyList[Volume] and
      (JsPath \ "restartPolicy").formatMaybeEmptyString() and
      (JsPath \ "terminationGracePeriodSeconds").formatNullable[Int] and
      (JsPath \ "activeDeadlineSeconds").formatNullable[Int] and
      (JsPath \ "dnsPolicy").format[DNSPolicy.Value] and
      (JsPath \ "nodeSelector").formatMaybeEmptyMap[String] and
      (JsPath \ "serviceAccountName").formatMaybeEmptyString() and
      (JsPath \ "nodeName").formatMaybeEmptyString() and
      (JsPath \ "hostNetwork").formatMaybeEmptyBoolean() and
      (JsPath \ "imagePullSecrets").formatMaybeEmptyList[LocalObjectReference]
    )(Pod.Spec.apply _, unlift(Pod.Spec.unapply))
    
  implicit lazy val podTemplSpecFormat: Format[Pod.Template.Spec] = Json.format[Pod.Template.Spec]
  implicit lazy val podTemplFormat : Format[Pod.Template] = (
      objFormat and
      (JsPath \ "spec").formatNullable[Pod.Template.Spec]
    ) (Pod.Template.apply _, unlift(Pod.Template.unapply))  
  
   implicit lazy val repCtrlrFormat: Format[ReplicationController] = (
    objFormat and
    (JsPath \ "spec").formatNullable[ReplicationController.Spec] and
    (JsPath \ "status").formatNullable[ReplicationController.Status]
  ) (ReplicationController.apply _, unlift(ReplicationController.unapply))
  
  implicit lazy val repCtrlrSpecFormat = Json.format[ReplicationController.Spec]
  implicit lazy val repCtrlrStatusFormat = Json.format[ReplicationController.Status]
  
}