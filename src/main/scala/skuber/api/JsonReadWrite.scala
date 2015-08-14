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
      
  // Many Kubernetes fields can be omitted if "empty" in the Json format. In some cases on this API
  // we use Option types to wrap the values, with None representing the empty case, and use the built-in 
  // formatNullable method to format them and handle the omitted case.
  // In other cases of certain types, the fact that Kubernetes interprets a specific value of that type 
  // as equivalent to empty leads us to dispense with Option wrapping (declutters client code in many cases) 
  // and rely on those same specific values to signify the empty case. These values are:
  // Maps, Lists: no members i.e. an empty list or map
  // Strings: zero length
  // Integers : 0 
  // Boolean: false
  // For these fields the custom Json formatters below are used to handle properly the omitted / empty case i.e.
  // - on writing, if the value in the Scala object is 'empty' per above, then it is omitted (by default)
  // - on reading, if the field is omitted in the Json then it is set to the 'empty' value in the resulting Scala object.
  // Under the covers they leverage formatNullable with intermediate Option representations of the values.
  
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
   
  // general formatting for Enumerations - derived from https://gist.github.com/mikesname/5237809
  def enumReads[E <: Enumeration](enum: E, default: Option[E#Value]=None): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) => {
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException => default match {
            case None => JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not appear to contain the value: '$s'")
            case Some(e) => JsSuccess(e)
          }
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
   
  implicit val protocolFmt = Format(enumReads(Protocol, Some(Protocol.TCP)), enumWrites)
  
  implicit val formatCntrProt: Format[Container.Port] = (
    (JsPath \ "containerPort").format[Int] and
    (JsPath \ "protocol").format[Protocol.Protocol] and
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
  
  
  implicit val nameablePortReads: Reads[NameablePort] = (
    (JsPath \ "port").read[Int].map(value => Left(value)) |
    (JsPath \ "port").read[String].map(value => Right(value) )
  )
  
     
  implicit val nameablePortWrite = Writes[NameablePort] { 
     value => value match {
       case Left(i) => (JsPath \ "port").write[Int].writes(i)
       case Right(s) => (JsPath \ "port").write[String].writes(s)
     }
  }
  
  implicit val nameablePortFormat: Format[NameablePort] = Format(nameablePortReads, nameablePortWrite)
  
  implicit val httpGetActionFormat: Format[HTTPGetAction] = (
      JsPath.format[NameablePort] and
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
  
   implicit val pullPolicyFormat: Format[Container.PullPolicy.Value] = 
       Format(enumReads(Container.PullPolicy, Some(Container.PullPolicy.IfNotPresent)), enumWrites)
       
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
    (JsPath \ "lifeCycle").formatNullable[Lifecycle] and
    (JsPath \ "terminationMessagePath").formatMaybeEmptyString() and
    (JsPath \ "imagePullPolicy").format[Container.PullPolicy.Value] and
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
        
  implicit val formatServiceSpecType: Format[Service.Type.Type] = 
    Format(enumReads(Service.Type,Some(Service.Type.ClusterIP)), enumWrites)
  implicit val formatServiceAffinity: Format[Service.Affinity.Affinity] = 
    Format(enumReads(Service.Affinity, Some(Service.Affinity.None)), enumWrites)
   
  implicit val servicePortFmt: Format[Service.Port] = (
    (JsPath \ "name").format[String] and
    (JsPath \ "protocol").format[Protocol.Value] and
    (JsPath \ "port").format[Int] and
    (JsPath \ "targetPort").formatNullable[NameablePort] and
    (JsPath \ "nodePort").formatMaybeEmptyInt()
  ) (Service.Port.apply _, unlift(Service.Port.unapply))
  
  implicit val serviceSpecFmt: Format[Service.Spec] = (
      (JsPath \ "ports").format[List[Service.Port]] and
      (JsPath \ "selector").formatMaybeEmptyMap[String] and
      (JsPath \ "clusterIP").formatMaybeEmptyString() and
      (JsPath \ "type").format[Service.Type.Type] and
      (JsPath \ "sessionAffinity").format[Service.Affinity.Affinity]
   )(Service.Spec.apply _, unlift(Service.Spec.unapply))  
   
  implicit val serviceFmt: Format[Service] = (
     objFormat and
     (JsPath \ "spec").formatNullable[Service.Spec] and
     (JsPath \ "status").formatNullable[Service.Status]
  )(Service.apply _, unlift(Service.unapply))
  
  implicit val endpointAddressFmt: Format[Endpoint.Address] = Json.format[Endpoint.Address]
  implicit val endpointPortFmt: Format[Endpoint.Port] = Json.format[Endpoint.Port]
  implicit val endpointSubsetFmt: Format[Endpoint.Subset] = Json.format[Endpoint.Subset]
  
  implicit val endpointFmt: Format[Endpoint] = (
    objFormat and
    (JsPath \ "subsets").format[List[Endpoint.Subset]]
  )(Endpoint.apply _, unlift(Endpoint.unapply))
  
  implicit val nodeSysInfoFmt: Format[Node.SystemInfo] = Json.format[Node.SystemInfo]
   
  implicit val nodeAddrFmt: Format[Node.Address] = (
    (JsPath \ "type").format[String] and
    (JsPath \ "address").format[String]
  )(Node.Address.apply _, unlift(Node.Address.unapply))
  
  implicit val nodePhaseFmt = enumFormat(Node.Phase)
 
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
    (JsPath \ "phase").formatNullable[Node.Phase.Phase] and
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
}