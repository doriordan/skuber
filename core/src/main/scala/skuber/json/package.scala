package skuber.json

import scala.language.implicitConversions
import java.time._
import java.time.format._
import org.apache.commons.codec.binary.Base64
import play.api.libs.functional.syntax._
import play.api.libs.json._
import skuber.model._
import Volume.{ConfigMapVolumeSource, KeyToPath}
import play.api.libs.functional.FunctionalBuilder
import skuber.api.client.{DeleteOptions, DeletePropagation, Preconditions}
import skuber.api.patch.{JsonPatch, JsonPatchOperation, MetadataPatch}

/**
 * @author David O'Riordan
 * Play/json formatters for the Skuber k8s model types
 */
package object format {

  // Formatters for the Java 8 ZonedDateTime objects that represent
  // (ISO 8601 / RFC 3329 compatible) Kubernetes timestamp fields
  implicit val timeWrites: Writes[Timestamp] = Writes.temporalWrites[ZonedDateTime, DateTimeFormatter](
      DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  @deprecated("Use timeWrites instead", "2.3.0")
  def timewWrites: Writes[Timestamp] = timeWrites
  implicit val timeReads: Reads[Timestamp] = Reads.DefaultZonedDateTimeReads

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
  implicit def maybeEmptyFormatMethods(path: JsPath): MaybeEmpty = new MaybeEmpty(path)

  // general formatting for Enumerations - derived from https://gist.github.com/mikesname/5237809
  implicit def enumReads[E <: Enumeration](enumeration: E) : Reads[enumeration.Value] = {
    case JsString(s) =>
      try {
        JsSuccess(enumeration.withName(s))
      } catch {
        case _: NoSuchElementException => JsError(s"Enumeration expected of type: '${enumeration.getClass}', but it does not appear to contain the value: '$s'")
      }
    case _ => JsError("String value expected")
  }

  implicit def enumReadsWithDefault[E <: Enumeration](enumeration: E)(default: enumeration.Value) : Reads[enumeration.Value] = enumReads(enumeration) or Reads.pure(default)

  implicit def enumWrites[E <: Enumeration](enumeration: E): Writes[enumeration.Value] = (v: enumeration.Value) => JsString(v.toString)

  implicit def enumFormat[E <: Enumeration](enumeration: E) : Format[enumeration.Value] = Format(enumReads(enumeration), enumWrites(enumeration))

  class EnumFormatter(val path: JsPath) {
     def formatEnum[E <: Enumeration](enumeration: E)(default: Option[enumeration.Value]=None) : OFormat[enumeration.Value] =
        path.formatNullable[String].inmap[enumeration.Value](_.map(s => enumeration.withName(s)).getOrElse(default.get), e =>  Some(e.toString))
     def formatNullableEnum[E <: Enumeration](enumeration: E)  : OFormat[Option[enumeration.Value]] =
        path.formatNullable[String].inmap[Option[enumeration.Value]](_.map(s => enumeration.withName(s)), e => e map { _.toString } )
  }
  implicit def enumFormatMethods(path: JsPath): EnumFormatter = new EnumFormatter(path)

  case class SelMatchExpression(
    key: String,
    operator: String = "Exists",
    values: Option[List[String]] = None
  )
  implicit val selMatchExpressionFmt: Format[SelMatchExpression] = Json.format[SelMatchExpression]
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
      case InRequirement(key,values) =>  SelMatchExpression(key, "In", Some(values))
      case NotInRequirement(key,values) =>  SelMatchExpression(key, "NotIn", Some(values))
    }.toList
    val matchExpressions = if(setBased.isEmpty) None else Some(setBased)

    OnTheWireSelector(matchLabels = matchLabels, matchExpressions = matchExpressions)
  }

  implicit val otwsFormat: Format[OnTheWireSelector] = Json.format[OnTheWireSelector]

  class LabelSelectorFormat(path: JsPath) {
    def formatNullableLabelSelector: OFormat[Option[LabelSelector]] =
      path.formatNullable[OnTheWireSelector].inmap[Option[LabelSelector]](
        _.map(otwSelectorToLabelSelector),
        selOpt => selOpt.map(labelSelToOtwSelector)
      )

    def formatLabelSelector: OFormat[LabelSelector] =
      path.format[OnTheWireSelector].inmap[LabelSelector](
        otwSelectorToLabelSelector,
        labelSelToOtwSelector
      )
  }

  implicit def jsPath2LabelSelFormat(path: JsPath): LabelSelectorFormat = new LabelSelectorFormat(path)

  // formatting of the Kubernetes types

  implicit lazy val objFormat: FunctionalBuilder[OFormat]#CanBuild3[Phase, Phase, ObjectMeta] =
    (JsPath \ "kind").formatMaybeEmptyString() and
    (JsPath \ "apiVersion").formatMaybeEmptyString() and
    (JsPath \ "metadata").lazyFormat[ObjectMeta](objectMetaFormat)
   // metadata format must be lazy as it can be used in indirectly recursive namespace structure (Namespace has a metadata.namespace field)

  def listResourceFormatBuilder[O <: ObjectResource](implicit f: Format[O]) =
    (JsPath \ "apiVersion").format[String] and
    (JsPath \ "kind").format[String] and
    (JsPath \ "metadata").formatNullable[ListMeta] and
    (JsPath \ "items").formatMaybeEmptyList[O]

  def ListResourceFormat[O <: ObjectResource](implicit f: Format[O]): OFormat[ListResource[O]] = listResourceFormatBuilder[O].apply(ListResource.apply, l => (l.apiVersion, l.kind, l.metadata, l.items))

  implicit val ownerRefFmt: Format[OwnerReference] = (
    (JsPath \ "apiVersion").formatMaybeEmptyString() and
    (JsPath \ "kind").formatMaybeEmptyString() and
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "uid").formatMaybeEmptyString() and
    (JsPath \ "controller").formatNullable[Boolean] and
    (JsPath \ "blockOwnerDeletion").formatNullable[Boolean]
  )(OwnerReference.apply, o => (o.apiVersion, o.kind, o.name, o.uid, o.controller, o.blockOwnerDeletion))

  implicit lazy val objectMetaFormat: Format[ObjectMeta] = (
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "generateName").formatMaybeEmptyString() and
    (JsPath \ "namespace").formatMaybeEmptyString() and
    (JsPath \ "uid").formatMaybeEmptyString() and
    (JsPath \ "selfLink").formatMaybeEmptyString() and
    (JsPath \ "resourceVersion").formatMaybeEmptyString() and
    (JsPath \ "creationTimestamp").formatNullable[Timestamp] and
    (JsPath \ "deletionTimestamp").formatNullable[Timestamp] and
    (JsPath \ "deletionGracePeriod").formatNullable[Int] and
    (JsPath \ "labels").formatMaybeEmptyMap[String] and
    (JsPath \ "annotations").formatMaybeEmptyMap[String] and
    (JsPath \ "ownerReferences").formatMaybeEmptyList[OwnerReference] and
    (JsPath \ "generation").formatMaybeEmptyInt() and
    (JsPath \ "finalizers").formatNullable[List[String]] and
    (JsPath \ "clusterName").formatNullable[String]
  )(ObjectMeta.apply, o => (
      o.name,
      o.generateName,
      o.namespace,
      o.uid,
      o.selfLink,
      o.resourceVersion,
      o.creationTimestamp,
      o.deletionTimestamp,
      o.deletionGracePeriodSeconds,
      o.labels, o.annotations,
      o.ownerReferences,
      o.generation,
      o.finalizers,
      o.clusterName)
  )

  implicit val listMetaFormat: Format[ListMeta] = (
    (JsPath \ "selfLink").formatMaybeEmptyString() and
    (JsPath \ "resourceVersion").formatMaybeEmptyString() and
    (JsPath \ "continue").formatNullable[String]
  )(ListMeta.apply, l => (l.selfLink, l.resourceVersion, l.continue))

  implicit val localObjRefFormat: Format[LocalObjectReference] = Json.format[LocalObjectReference]

  implicit val apiVersionsFormat: Format[APIVersions] = Json.format[APIVersions]

  implicit val objRefFormat: Format[ObjectReference] = (
    (JsPath \ "kind").formatMaybeEmptyString() and
    (JsPath \ "apiVersion").formatMaybeEmptyString() and
    (JsPath \ "namespace").formatMaybeEmptyString() and
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "uid").formatMaybeEmptyString() and
    (JsPath \ "resourceVersion").formatMaybeEmptyString() and
    (JsPath \ "fieldPath").formatMaybeEmptyString()
  )(ObjectReference.apply, o => (o.kind, o.apiVersion, o.namespace, o.name, o.uid, o.resourceVersion, o.fieldPath))

  implicit val nsSpecFormat: Format[Namespace.Spec] = Json.format[Namespace.Spec]
  implicit val nsStatusFormat: Format[Namespace.Status] = Json.format[Namespace.Status]

  implicit lazy val namespaceFormat : Format[Namespace] = (
    objFormat and
    (JsPath \ "spec").formatNullable[Namespace.Spec] and
    (JsPath \ "status").formatNullable[Namespace.Status]
  ) (Namespace.apply, n => (n.kind, n.apiVersion, n.metadata, n.spec, n.status))

  implicit val secSELFormat: Format[Security.SELinuxOptions] = (
    (JsPath \ "user").formatMaybeEmptyString() and
    (JsPath \ "role").formatMaybeEmptyString() and
    (JsPath \ "type").formatMaybeEmptyString() and
    (JsPath \ "level").formatMaybeEmptyString()
   )(Security.SELinuxOptions.apply, s => (s.user, s.role, s._type, s.level))

  implicit val secCapabFormat: Format[Security.Capabilities] = (
    (JsPath \ "add").formatMaybeEmptyList[Security.Capability] and
    (JsPath \ "drop").formatMaybeEmptyList[Security.Capability]
  )(Security.Capabilities.apply, s => (s.add, s.drop))

  implicit val secSysctlFormat: Format[Security.Sysctl] = Json.format[Security.Sysctl]

  implicit val secCtxtFormat: Format[SecurityContext] = Json.format[SecurityContext]

  implicit val podSecCtxtFormat: Format[PodSecurityContext] = (
    (JsPath \ "fsGroup").formatNullable[Int] and
    (JsPath \ "runAsGroup").formatNullable[Int] and
    (JsPath \ "runAsNonRoot").formatNullable[Boolean] and
    (JsPath \ "runAsUser").formatNullable[Int] and
    (JsPath \ "seLinuxOptions").formatNullable[Security.SELinuxOptions] and
    (JsPath \ "supplementalGroups").formatMaybeEmptyList[Int] and
    (JsPath \ "sysctls").formatMaybeEmptyList[Security.Sysctl]
  )(PodSecurityContext.apply, p => (p.fsGroup, p.runAsGroup, p.runAsNonRoot, p.runAsUser, p.seLinuxOptions, p.supplementalGroups, p.sysctls))

  implicit val tolerationEffectFmt: Format[Pod.TolerationEffect] = new Format[Pod.TolerationEffect] {

    import skuber.model.Pod.TolerationEffect._

    override def reads(json: JsValue): JsResult[Pod.TolerationEffect] = json match {
      case JsString(value) => value match {
        case NoSchedule.name => JsSuccess(NoSchedule)
        case PreferNoSchedule.name => JsSuccess(PreferNoSchedule)
        case NoExecute.name => JsSuccess(NoExecute)
        case name => JsError(s"Unknown toleration effect '$name'")
      }
      case _ => JsError(s"Toleration effect should be a string")
    }

    override def writes(effect: Pod.TolerationEffect): JsValue = effect match {
      case NoSchedule => JsString(NoSchedule.name)
      case PreferNoSchedule => JsString(PreferNoSchedule.name)
      case NoExecute => JsString(NoExecute.name)
    }
  }

  implicit val tolerationFmt: Format[Pod.Toleration] = new Format[Pod.Toleration] {

    override def reads(json: JsValue): JsResult[Pod.Toleration] = json match {
      case JsObject(fields) =>

        val tolerationSeconds=fields.get("tolerationSeconds").map(js => js.as[Int])
        val effect: Option[Pod.TolerationEffect] = fields.get("effect").flatMap{
          case JsNull => None
          case e @ _ => Some(e.as[Pod.TolerationEffect])
        }

        fields.get("operator") match {
          case Some(JsString("Equal")) | None =>
            val key = fields("key").as[String]
            val value = fields.get("value").map(js => js.as[String])
            JsSuccess(Pod.EqualToleration(key, value, effect, tolerationSeconds))
          case Some(JsString("Exists"))  =>
            val key = fields.get("key").map(js => js.as[String])
            JsSuccess(Pod.ExistsToleration(key, effect, tolerationSeconds))
          case operator => JsError(s"Unknown operator '$operator'")
        }

      case _ => JsError(s"Unknown toleration")
    }

    override def writes(toleration: Pod.Toleration): JsValue = toleration match {

      case Pod.EqualToleration(key, value, effect, tolerationSeconds) =>
        val fields: List[(String, JsValue)] =  List(
          Some("key" -> JsString(key)),
          value.map(v => "value" -> JsString(v)),
          Some("operator" -> JsString("Equal")),
          effect.map(e => "effect" -> Json.toJson(e)),
          tolerationSeconds.map(ts => "tolerationSeconds" -> JsNumber(ts))).flatten
        JsObject(fields)
      case Pod.ExistsToleration(key, effect, tolerationSeconds) =>
        val fields: List[(String, JsValue)] = List(
          key.map(k => "key" -> JsString(k)),
          Some("operator" -> JsString("Exists")),
          effect.map(e => "effect" -> Json.toJson(e)),
          tolerationSeconds.map(ts => "tolerationSeconds" -> JsNumber(ts))).flatten
        JsObject(fields)
    }
  }

  implicit val envVarFldRefFmt: Format[EnvVar.FieldRef] = (
    (JsPath \ "fieldPath").format[String] and
    (JsPath \ "apiVersion").formatMaybeEmptyString()
  )(EnvVar.FieldRef.apply, e => (e.fieldPath, e.apiVersion))

  implicit val envVarCfgMapRefFmt: Format[EnvVar.ConfigMapKeyRef] = Json.format[EnvVar.ConfigMapKeyRef]
  implicit val envVarSecKeyRefFmt: Format[EnvVar.SecretKeyRef] = Json.format[EnvVar.SecretKeyRef]

  implicit val envVarValueWrite: Writes[EnvVar.Value] = Writes[EnvVar.Value] {
    case EnvVar.StringValue(str) => (JsPath \ "value").write[String].writes(str)
    case fr: EnvVar.FieldRef => (JsPath \ "valueFrom" \ "fieldRef").write[EnvVar.FieldRef].writes(fr)
    case cmr: EnvVar.ConfigMapKeyRef => (JsPath \ "valueFrom" \ "configMapKeyRef").write[EnvVar.ConfigMapKeyRef].writes(cmr)
    case skr: EnvVar.SecretKeyRef => (JsPath \ "valueFrom" \ "secretKeyRef").write[EnvVar.SecretKeyRef].writes(skr)
  }

  implicit val envVarValueReads: Reads[EnvVar.Value] =
    (JsPath \ "valueFrom" \ "fieldRef").read[EnvVar.FieldRef].map(x => x: EnvVar.Value) |
    (JsPath \ "valueFrom" \ "configMapKeyRef").read[EnvVar.ConfigMapKeyRef].map(x => x: EnvVar.Value) |
    (JsPath \ "valueFrom" \ "secretKeyRef").read[EnvVar.SecretKeyRef].map(x => x: EnvVar.Value) |
    (JsPath \ "value").readNullable[String].map(value => EnvVar.StringValue(value.getOrElse("")))

   implicit val envVarWrites : Writes[EnvVar] = (
    (JsPath \ "name").write[String] and
    JsPath.write[EnvVar.Value]
  )(e => (e.name, e.value))

  implicit val envVarReads: Reads[EnvVar] = (
    (JsPath \ "name").read[String] and
    JsPath.read[EnvVar.Value]
  )(EnvVar.apply _)

  implicit val envVarFormat: Format[EnvVar] = Format(envVarReads, envVarWrites)

  implicit val configMapEnvSourceFmt: Format[EnvFromSource.ConfigMapEnvSource] = Json.format[EnvFromSource.ConfigMapEnvSource]
  implicit val secretRefEnvSourceFmt: Format[EnvFromSource.SecretEnvSource] = Json.format[EnvFromSource.SecretEnvSource]

  implicit val envSourceReads: Reads[EnvFromSource.EnvSource] =
    (JsPath \ "configMapRef").read[EnvFromSource.ConfigMapEnvSource].map(x => x: EnvFromSource.EnvSource) |
    (JsPath \ "secretRef").read[EnvFromSource.SecretEnvSource].map(x => x: EnvFromSource.EnvSource)

  implicit val envSourceWrite: Writes[EnvFromSource.EnvSource] = Writes[EnvFromSource.EnvSource] {
    case c: EnvFromSource.ConfigMapEnvSource => (JsPath \ "configMapRef").write[EnvFromSource.ConfigMapEnvSource].writes(c)
    case s: EnvFromSource.SecretEnvSource => (JsPath \ "secretRef").write[EnvFromSource.SecretEnvSource].writes(s)
  }

  implicit val envFromSourceReads: Reads[EnvFromSource] = (
    (JsPath \ "prefix").readNullable[String] and
    JsPath.read[EnvFromSource.EnvSource]
  )(EnvFromSource.apply _)

  implicit val envFromSourceWrites: Writes[EnvFromSource] = (
    (JsPath \ "prefix").writeNullable[String] and
    JsPath.write[EnvFromSource.EnvSource]
  )(e => (e.prefix, e.source))

  implicit val envFromSourceFmt: Format[EnvFromSource] = Format(envFromSourceReads, envFromSourceWrites)

  implicit val quantityFormat: Format[Resource.Quantity] = new Format[Resource.Quantity] {
     // Note: validate on read in future?
     def reads(json: JsValue): JsResult[Resource.Quantity] =
        Json.fromJson[String](json).flatMap { s => JsSuccess(Resource.Quantity(s)) }
     def writes(o: Resource.Quantity): JsValue = Json.toJson(o.value)
  }

  implicit val resRqtsFormat: Format[Resource.Requirements] = (
    (JsPath \ "limits").formatMaybeEmptyMap[Resource.Quantity] and
    (JsPath \ "requests").formatMaybeEmptyMap[Resource.Quantity]
  )(Resource.Requirements.apply, r => (r.limits, r.requests))

  implicit val protocolFmt: Format[skuber.model.Protocol.Value] = Format(enumReadsWithDefault(Protocol)(Protocol.TCP), enumWrites(Protocol))

  implicit val formatCntrProt: Format[Container.Port] = (
    (JsPath \ "containerPort").format[Int] and
    (JsPath \ "protocol").formatEnum(Protocol)(Some(Protocol.TCP)) and
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "hostIP").formatMaybeEmptyString() and
    (JsPath \ "hostPort").formatNullable[Int]
  )(Container.Port.apply, r => (r.containerPort, r.protocol, r.name, r.hostIP, r.hostPort))

  implicit val cntrStateWaitingFormat: Format[Container.Waiting] =Json.format[Container.Waiting]
  implicit val cntrStateRunningFormat: Format[Container.Running] =Json.format[Container.Running]
  implicit val cntrStateTerminatedFormat: Format[Container.Terminated] =Json.format[Container.Terminated]

  implicit val cntrStateReads: Reads[Container.State] =
    (JsPath \ "waiting").read[Container.Waiting].map(x => x: Container.State) |
    (JsPath \ "running").read[Container.Running].map(x => x: Container.State) |
    (JsPath \ "terminated").read[Container.Terminated].map(x => x: Container.State) |
    Reads.pure(Container.Waiting()) // default

  implicit val cntrStateWrites: Writes[Container.State] = Writes[Container.State] {
    case wt: Container.Waiting => (JsPath \ "waiting").write[Container.Waiting](cntrStateWaitingFormat).writes(wt)
    case rn: Container.Running => (JsPath \ "running").write[Container.Running](cntrStateRunningFormat).writes(rn)
    case te: Container.Terminated => (JsPath \ "terminated").write[Container.Terminated](cntrStateTerminatedFormat).writes(te)
  }

  implicit val cntrStatusFormat: Format[Container.Status] = Json.format[Container.Status]

  implicit val execActionFormat: Format[ExecAction] =
    (JsPath \ "command").format[List[String]].inmap(cmd => ExecAction(cmd), (ea: ExecAction) => ea.command)

  implicit val intOrStrReads: Reads[IntOrString] =
    JsPath.read[Int].map(value => Left(value)) |
    JsPath.read[String].map(value => Right(value) )

  implicit val intOrStrWrites: Writes[IntOrString] = Writes[IntOrString] {
    case Left(i) => Writes.IntWrites.writes(i)
    case Right(s) => Writes.StringWrites.writes(s)
  }

  implicit val intOrStringFormat: Format[IntOrString] = Format(intOrStrReads, intOrStrWrites)

  implicit val httpGetActionFormat: Format[HTTPGetAction] = (
    (JsPath \ "port").format[NameablePort] and
    (JsPath \ "host").formatMaybeEmptyString() and
    (JsPath \ "path").formatMaybeEmptyString() and
    (JsPath \ "scheme").formatMaybeEmptyString()
   )(HTTPGetAction.apply, a => (a.port, a.host, a.path, a.schema))

  implicit val tcpSocketActionFormat: Format[TCPSocketAction] =
    (JsPath \ "port").format[NameablePort].inmap(port => TCPSocketAction(port), (tsa: TCPSocketAction) => tsa.port)

  implicit val handlerReads: Reads[Handler] =
    (JsPath \ "exec").read[ExecAction].map(x => x:Handler) |
    (JsPath \ "httpGet").read[HTTPGetAction].map(x => x:Handler) |
    (JsPath \ "tcpSocket").read[TCPSocketAction].map(x => x:Handler)

  implicit val handlerWrites: Writes[Handler] = Writes[Handler] {
    case ea: ExecAction => (JsPath \ "exec").write[ExecAction](execActionFormat).writes(ea)
    case hga: HTTPGetAction => (JsPath \ "httpGet").write[HTTPGetAction](httpGetActionFormat).writes(hga)
    case tsa: TCPSocketAction => (JsPath \ "tcpSocket").write[TCPSocketAction](tcpSocketActionFormat).writes(tsa)
  }
  implicit val handlerFormat: Format[Handler] = Format(handlerReads, handlerWrites)

  implicit val probeFormat : Format[Probe] = (
    JsPath.format[Handler] and
    (JsPath \ "initialDelaySeconds").formatMaybeEmptyInt() and
    (JsPath \ "timeoutSeconds").formatMaybeEmptyInt() and
    (JsPath \ "periodSeconds").formatNullable[Int] and
    (JsPath \ "successThreshold").formatNullable[Int] and
    (JsPath \ "failureThreshold").formatNullable[Int]
  )(Probe.apply, p => (p.action, p.initialDelaySeconds, p.timeoutSeconds, p.periodSeconds, p.successThreshold, p.failureThreshold))

  implicit val lifecycleFormat: Format[Lifecycle] = Json.format[Lifecycle]

  implicit val storageMediumFormat: Format[Volume.StorageMedium] = Format[Volume.StorageMedium](Reads[Volume.StorageMedium] {
    case JsString(med) if med == "Memory" => JsSuccess(Volume.MemoryStorageMedium)
    case JsString(med) if med == "HugePages" => JsSuccess(Volume.HugePagesStorageMedium)
    case _ => JsSuccess(Volume.DefaultStorageMedium)
  }, Writes[Volume.StorageMedium] {
    case Volume.DefaultStorageMedium => JsString("")
    case Volume.MemoryStorageMedium => JsString("Memory")
    case Volume.HugePagesStorageMedium => JsString("HugePages")
  })

  implicit val emptyDirFormat: Format[Volume.EmptyDir] = (
    (JsPath \ "medium").formatWithDefault[Volume.StorageMedium](Volume.DefaultStorageMedium) and
    (JsPath \ "sizeLimit").formatNullable[Resource.Quantity]
  )(Volume.EmptyDir.apply, e => (e.medium, e.sizeLimit))

  implicit val hostPathFormat: Format[Volume.HostPath] = Json.format[Volume.HostPath]
  implicit val keyToPathFormat: Format[KeyToPath] = Json.format[KeyToPath]
  implicit val volumeSecretFormat: Format[Volume.Secret] = Json.format[Volume.Secret]
  implicit val gitFormat: Format[Volume.GitRepo] = Json.format[Volume.GitRepo]

  implicit val objectFieldSelectorFormat: Format[Volume.ObjectFieldSelector] = (
    (JsPath \ "apiVersion").formatMaybeEmptyString() and
    (JsPath \ "fieldPath").format[String]
  )(Volume.ObjectFieldSelector.apply, o => (o.apiVersion, o.fieldPath))

  implicit val resourceFieldSelectorFormat: Format[Volume.ResourceFieldSelector] = Json.format[Volume.ResourceFieldSelector]
  implicit val downwardApiVolumeFileFormat: Format[Volume.DownwardApiVolumeFile] = Json.format[Volume.DownwardApiVolumeFile]

  implicit val downwardApiVolumeSourceFormat: Format[Volume.DownwardApiVolumeSource] = (
    (JsPath \ "defaultMode").formatNullable[Int] and
    (JsPath \ "items").formatMaybeEmptyList[Volume.DownwardApiVolumeFile]
  )(Volume.DownwardApiVolumeSource.apply, d => (d.defaultMode, d.items))

  implicit val configMapVolFormat: Format[ConfigMapVolumeSource] = (
    (JsPath \ "name").format[String] and
    (JsPath \ "items").formatMaybeEmptyList[KeyToPath] and
    (JsPath \ "defaultMode").formatNullable[Int] and
    (JsPath \ "optional").formatNullable[Boolean]
  )(ConfigMapVolumeSource.apply, c => (c.name, c.items, c.defaultMode, c.optional))

  implicit  val gceFormat: Format[Volume.GCEPersistentDisk] = (
    (JsPath \ "pdName").format[String] and
    (JsPath \ "fsType").format[String] and
    (JsPath \ "partition").formatMaybeEmptyInt() and
    (JsPath \ "readOnly").formatMaybeEmptyBoolean()
  )(Volume.GCEPersistentDisk.apply, g => (g.pdName, g.fsType, g.partition, g.readOnly))

   implicit val awsFormat: Format[Volume.AWSElasticBlockStore] = (
     (JsPath \ "volumeID").format[String] and
     (JsPath \ "fsType").format[String] and
     (JsPath \ "partition").formatMaybeEmptyInt() and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(Volume.AWSElasticBlockStore.apply, a => (a.volumeID, a.fsType, a.partition, a.readOnly))

  implicit val nfsFormat: Format[Volume.NFS] = (
    (JsPath \ "server").format[String] and
    (JsPath \ "path").format[String] and
    (JsPath \ "readOnly").formatMaybeEmptyBoolean()
  )(Volume.NFS.apply, n => (n.server, n.path, n.readOnly))

  implicit val glusterfsFormat: Format[Volume.Glusterfs] = (
    (JsPath \ "endpoints").format[String] and
    (JsPath \ "path").format[String] and
    (JsPath \ "readOnly").formatMaybeEmptyBoolean()
  )(Volume.Glusterfs.apply, g => (g.endpointsName, g.path, g.readOnly))

  implicit val rbdFormat: Format[Volume.RBD] = (
    (JsPath \ "monitors").format[List[String]] and
    (JsPath \ "image").format[String] and
    (JsPath \ "fsType").format[String] and
    (JsPath \ "pool").formatMaybeEmptyString() and
    (JsPath \ "user").formatMaybeEmptyString() and
    (JsPath \ "keyring").formatMaybeEmptyString() and
    (JsPath \ "secretRef").formatNullable[LocalObjectReference] and
    (JsPath \ "readOnly").formatMaybeEmptyBoolean()
  )(Volume.RBD.apply, r => (r.monitors, r.image, r.fsType, r.pool, r.user, r.keyring, r.secretRef, r.readOnly))

   implicit val iscsiFormat: Format[Volume.ISCSI] = (
     (JsPath \ "targetPortal").format[String] and
     (JsPath \ "iqn").format[String] and
     (JsPath \ "portals").formatMaybeEmptyList[String] and
     (JsPath \ "lun").format[Int] and
     (JsPath \ "fsType").format[String] and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(Volume.ISCSI.apply, i => (i.targetPortal, i.iqn, i.portals, i.lun, i.fsType, i.readOnly))

   implicit val persistentVolumeClaimRefFormat: Format[Volume.PersistentVolumeClaimRef] = (
     (JsPath \ "claimName").format[String] and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean()
   )(Volume.PersistentVolumeClaimRef.apply, p => (p.claimName, p.readOnly))

   implicit val persVolumeSourceReads: Reads[Volume.PersistentSource] =
     (JsPath \ "hostPath").read[Volume.HostPath].map(x => x: Volume.PersistentSource) |
     (JsPath \ "awsElasticBlockStore").read[Volume.AWSElasticBlockStore].map(x => x: Volume.PersistentSource) |
     (JsPath \ "nfs").read[Volume.NFS].map(x => x: Volume.PersistentSource) |
     (JsPath \ "glusterfs").read[Volume.Glusterfs].map(x => x: Volume.PersistentSource) |
     (JsPath \ "rbd").read[Volume.RBD].map(x => x: Volume.PersistentSource) |
     (JsPath \ "iscsi").read[Volume.ISCSI].map(x => x: Volume.PersistentSource) |
     (JsPath \ "gcePersistentDisk").read[Volume.GCEPersistentDisk].map(x => x: Volume.PersistentSource) |
     JsPath.read[JsValue].map[Volume.PersistentSource](j => Volume.GenericVolumeSource(j.toString))

  implicit val secretProjectionFormat: Format[Volume.SecretProjection] = Json.format[Volume.SecretProjection]
  implicit val configMapProjectionFormat: Format[Volume.ConfigMapProjection] = Json.format[Volume.ConfigMapProjection]
  implicit val downwardApiProjectionFormat: Format[Volume.DownwardAPIProjection] = Json.format[Volume.DownwardAPIProjection]
  implicit val serviceAccountTokenProjectionFormat: Format[Volume.ServiceAccountTokenProjection] = Json.format[Volume.ServiceAccountTokenProjection]

  implicit val projectedVolumeSourceWrites: Writes[Volume.VolumeProjection] = Writes[Volume.VolumeProjection] {
    case s: Volume.SecretProjection => (JsPath \ "secret").write[Volume.SecretProjection](secretProjectionFormat).writes(s)
    case cm: Volume.ConfigMapProjection => (JsPath \ "configMap").write[Volume.ConfigMapProjection](configMapProjectionFormat).writes(cm)
    case dapi: Volume.DownwardAPIProjection => (JsPath \ "downwardAPI").write[Volume.DownwardAPIProjection](downwardApiProjectionFormat).writes(dapi)
    case sa: Volume.ServiceAccountTokenProjection => (JsPath \ "serviceAccountToken").write[Volume.ServiceAccountTokenProjection](serviceAccountTokenProjectionFormat).writes(sa)
  }

  implicit val projectedFormat: Format[Volume.ProjectedVolumeSource] = new Format[Volume.ProjectedVolumeSource] {
    override def writes(o: Volume.ProjectedVolumeSource): JsValue = Json.writes[Volume.ProjectedVolumeSource].writes(o)

    override def reads(json: JsValue): JsResult[Volume.ProjectedVolumeSource] =
      JsSuccess(Volume.ProjectedVolumeSource(
        (json \ "defaultMode").asOpt[Int],
        (json \ "sources").as[List[JsObject]].flatMap(s => {
          s.keys.headOption map {
            case "secret" => s.value("secret").as[Volume.SecretProjection]
            case "configMap" => s.value("configMap").as[Volume.ConfigMapProjection]
            case "downwardAPI" => s.value("downwardAPI").as[Volume.DownwardAPIProjection]
            case "serviceAccountToken" => s.value("serviceAccountToken").as[Volume.ServiceAccountTokenProjection]
          }
        })))
  }

   implicit val volumeSourceReads: Reads[Volume.Source] =
     (JsPath \ "emptyDir").read[Volume.EmptyDir].map(x => x: Volume.Source) |
     (JsPath \ "projected").read[Volume.ProjectedVolumeSource].map(x => x: Volume.Source) |
     (JsPath \ "secret").read[Volume.Secret].map(x => x: Volume.Source) |
     (JsPath \ "configMap").read[ConfigMapVolumeSource].map(x => x: Volume.Source) |
     (JsPath \ "gitRepo").read[Volume.GitRepo].map(x => x: Volume.Source) |
     (JsPath \ "persistentVolumeClaim").read[Volume.PersistentVolumeClaimRef].map(x => x: Volume.Source) |
     (JsPath \ "downwardAPI").read[Volume.DownwardApiVolumeSource].map(x => x: Volume.Source) |
     persVolumeSourceReads.map(x => x: Volume.Source)

   implicit val persVolumeSourceWrites: Writes[Volume.PersistentSource] = Writes[Volume.PersistentSource] {
     case hp: Volume.HostPath => (JsPath \ "hostPath").write[Volume.HostPath](hostPathFormat).writes(hp)
     case gced: Volume.GCEPersistentDisk => (JsPath \ "gcePersistentDisk").write[Volume.GCEPersistentDisk](gceFormat).writes(gced)
     case awse: Volume.AWSElasticBlockStore => (JsPath \ "awsElasticBlockStore").write[Volume.AWSElasticBlockStore](awsFormat).writes(awse)
     case nfs: Volume.NFS => (JsPath \ "nfs").write[Volume.NFS](nfsFormat).writes(nfs)
     case gfs: Volume.Glusterfs => (JsPath \ "glusterfs").write[Volume.Glusterfs](glusterfsFormat).writes(gfs)
     case rbd: Volume.RBD => (JsPath \ "rbd").write[Volume.RBD](rbdFormat).writes(rbd)
     case iscsi: Volume.ISCSI => (JsPath \ "iscsi").write[Volume.ISCSI](iscsiFormat).writes(iscsi)
     case Volume.GenericVolumeSource(json) => Json.parse(json)
   }

   implicit val volumeSourceWrites: Writes[Volume.Source] = Writes[Volume.Source] {
       case ps: Volume.PersistentSource => persVolumeSourceWrites.writes(ps)
       case ed: Volume.EmptyDir => (JsPath \ "emptyDir").write[Volume.EmptyDir](emptyDirFormat).writes(ed)
       case p: Volume.ProjectedVolumeSource => (JsPath \ "projected").write[Volume.ProjectedVolumeSource](projectedFormat).writes(p)
       case secr: Volume.Secret => (JsPath \ "secret").write[Volume.Secret](volumeSecretFormat).writes(secr)
       case cfgMp: ConfigMapVolumeSource => (JsPath \ "configMap").write[ConfigMapVolumeSource](configMapVolFormat).writes(cfgMp)
       case gitr: Volume.GitRepo => (JsPath \ "gitRepo").write[Volume.GitRepo](gitFormat).writes(gitr)
       case da: Volume.DownwardApiVolumeSource => (JsPath \ "downwardAPI").write[Volume.DownwardApiVolumeSource](downwardApiVolumeSourceFormat).writes(da)
       case pvc: Volume.PersistentVolumeClaimRef => (JsPath \ "persistentVolumeClaim").write[Volume.PersistentVolumeClaimRef](persistentVolumeClaimRefFormat).writes(pvc)
   }

   implicit val volumeReads: Reads[Volume] = (
     (JsPath \ "name").read[String] and
     volumeSourceReads
   )(Volume.apply _)


   implicit val volumeWrites: Writes[Volume] = (
     (JsPath \ "name").write[String] and
     JsPath.write[Volume.Source]
   )(v => (v.name, v.source))

   implicit val volumeFormat: Format[Volume] = Format(volumeReads, volumeWrites)

   implicit val persVolSourceFormat: Format[Volume.PersistentSource] = Format(persVolumeSourceReads, persVolumeSourceWrites)

   implicit val volMountFormat: Format[Volume.Mount] = (
     (JsPath \ "name").format[String] and
     (JsPath \ "mountPath").format[String] and
     (JsPath \ "readOnly").formatMaybeEmptyBoolean() and
     (JsPath \ "subPath").formatMaybeEmptyString() and
     (JsPath \ "mountPropagation").formatNullableEnum(Volume.MountPropagationMode)
   )(Volume.Mount.apply, v => (v.name, v.mountPath, v.readOnly, v.subPath, v.mountPropagation))

  implicit val volDeviceFmt: Format[Volume.Device] = Json.format[Volume.Device]

  implicit val pullPolicyFormat: Format[Container.PullPolicy.Value] =
       Format(enumReadsWithDefault(Container.PullPolicy)(Container.PullPolicy.IfNotPresent), enumWrites(Container.PullPolicy))

  implicit val terminationMessagePolicyFormat: Format[Container.TerminationMessagePolicy.Value] =
    Format(enumReadsWithDefault(Container.TerminationMessagePolicy)(Container.TerminationMessagePolicy.File), enumWrites(Container.TerminationMessagePolicy))

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
    (JsPath \ "terminationMessagePath").formatNullable[String] and
    (JsPath \ "terminationMessagePolicy").formatNullableEnum(Container.TerminationMessagePolicy)  and
    (JsPath \ "imagePullPolicy").formatEnum(Container.PullPolicy)(Some(Container.PullPolicy.IfNotPresent)) and
    (JsPath \ "securityContext").formatNullable[SecurityContext] and
    (JsPath \ "envFrom").formatMaybeEmptyList[EnvFromSource] and
    (JsPath \ "stdin").formatNullable[Boolean] and
    (JsPath \ "stdinOnce").formatNullable[Boolean] and
    (JsPath \ "tty").formatNullable[Boolean] and
    (JsPath \ "volumeDevices").formatMaybeEmptyList[Volume.Device] and
    (JsPath \ "startupProbe").formatNullable[Probe] 
  )(Container.apply, c => (
      c.name,
      c.image,
      c.command,
      c.args,
      c.workingDir,
      c.ports,
      c.env,
      c.resources,
      c.volumeMounts,
      c.livenessProbe,
      c.readinessProbe,
      c.lifecycle,
      c.terminationMessagePath,
      c.terminationMessagePolicy,
      c.imagePullPolicy,
      c.securityContext,
      c.envFrom,
      c.stdin,
      c.stdinOnce,
      c.tty,
      c.volumeDevices,
      c.startupProbe
  ))

  implicit val cntnrImageFmt: Format[Container.Image] = (
    (JsPath \ "names").formatMaybeEmptyList[String] and
    (JsPath \ "sizeBytes").formatNullable[Long]
  )(Container.Image.apply, c => (c.names, c.sizeBytes))

  implicit val podStatusCondFormat : Format[Pod.Condition] = (
      (JsPath \ "type").format[String] and
      (JsPath \ "status").formatMaybeEmptyString() and
      (JsPath \ "reason").formatNullable[String] and
      (JsPath \ "message").formatNullable[String] and
      (JsPath \ "lastProbeTime").formatNullable[Timestamp] and
      (JsPath \ "lastTransitionTime").formatNullable[Timestamp]
    )(Pod.Condition.apply, c => (c._type, c.status, c.reason, c.message, c.lastProbeTime, c.lastTransitionTime))

  implicit val podStatusFormat: Format[Pod.Status] = (
      (JsPath \ "phase").formatNullableEnum(Pod.Phase) and
      (JsPath \ "conditions").formatMaybeEmptyList[Pod.Condition] and
      (JsPath \ "message").formatNullable[String] and
      (JsPath \ "reason").formatNullable[String] and
      (JsPath \ "hostIP").formatNullable[String] and
      (JsPath \ "podIP").formatNullable[String] and
      (JsPath \ "startTime").formatNullable[Timestamp] and
      (JsPath \ "containerStatuses").formatMaybeEmptyList[Container.Status] and
      (JsPath \ "initContainerStatuses").formatMaybeEmptyList[Container.Status] and
      (JsPath \ "qosClass").formatNullable[String] and
      (JsPath \ "nominatedNodeName").formatNullable[String]
    )(Pod.Status.apply, p => (p.phase, p.conditions, p.message, p.reason, p.hostIP, p.podIP, p.startTime, p.containerStatuses, p.initContainerStatuses, p.qosClass, p.nominatedNodeName))

  implicit lazy val podFormat : Format[Pod] = (
      objFormat and
      (JsPath \ "spec").formatNullable[Pod.Spec] and
      (JsPath \ "status").formatNullable[Pod.Status]
    ) (Pod.apply, p => (p.kind, p.apiVersion, p.metadata, p.spec, p.status))

  implicit val nodeAffinityOperatorFormat: Format[Pod.Affinity.NodeSelectorOperator.Operator] = Format(enumReads(Pod.Affinity.NodeSelectorOperator), enumWrites(Pod.Affinity.NodeSelectorOperator))

  implicit val nodeMatchExpressionFormat: Format[Pod.Affinity.NodeSelectorRequirement] = (
    (JsPath \ "key").formatMaybeEmptyString() and
    (JsPath \ "operator").formatEnum(Pod.Affinity.NodeSelectorOperator)(None) and
    (JsPath \ "values").formatMaybeEmptyList[String]
  )(Pod.Affinity.NodeSelectorRequirement.apply, n => (n.key, n.operator, n.values))

  implicit val nodeSelectorTermFormat: Format[Pod.Affinity.NodeSelectorTerm] = (
    (JsPath \ "matchExpressions").formatMaybeEmptyList[Pod.Affinity.NodeSelectorRequirement] and
      (JsPath \ "matchFields").formatMaybeEmptyList[Pod.Affinity.NodeSelectorRequirement]
    )(Pod.Affinity.NodeSelectorTerm.apply, n => (n.matchExpressions, n.matchFields))

  implicit val nodeRequiredDuringSchedulingIgnoredDuringExecutionFormat: Format[Pod.Affinity.NodeAffinity.RequiredDuringSchedulingIgnoredDuringExecution] =
    (JsPath \ "nodeSelectorTerms").format[Pod.Affinity.NodeSelectorTerms].inmap(
      nodeSelectorTerms => Pod.Affinity.NodeAffinity.RequiredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms),
      (rdside: Pod.Affinity.NodeAffinity.RequiredDuringSchedulingIgnoredDuringExecution) => rdside.nodeSelectorTerms
    )

  implicit lazy val nodePreferredSchedulingTermFormat : Format[Pod.Affinity.NodeAffinity.PreferredSchedulingTerm] = Json.format[Pod.Affinity.NodeAffinity.PreferredSchedulingTerm]

  implicit lazy val nodeAffinityFormat : Format[Pod.Affinity.NodeAffinity] = (
    (JsPath \ "requiredDuringSchedulingIgnoredDuringExecution").formatNullable[Pod.Affinity.NodeAffinity.RequiredDuringSchedulingIgnoredDuringExecution] and
    (JsPath \ "preferredDuringSchedulingIgnoredDuringExecution").formatMaybeEmptyList[Pod.Affinity.NodeAffinity.PreferredSchedulingTerm]
  )(Pod.Affinity.NodeAffinity.apply, n => (n.requiredDuringSchedulingIgnoredDuringExecution, n.preferredDuringSchedulingIgnoredDuringExecution))

  implicit lazy val podAffinityTermFormat: Format[Pod.Affinity.PodAffinityTerm] = (
    (JsPath \ "labelSelector").formatNullableLabelSelector and
    (JsPath \ "namespaces").formatMaybeEmptyList[String] and
    (JsPath \ "topologyKey").format[String]
  )(Pod.Affinity.PodAffinityTerm.apply, n => (n.labelSelector, n.namespaces, n.topologyKey))

  implicit lazy val weightedPodAffinityTermFmt: Format[Pod.Affinity.WeightedPodAffinityTerm] = Json.format[Pod.Affinity.WeightedPodAffinityTerm]

  implicit lazy val podAffinityFormat: Format[Pod.Affinity.PodAffinity] = (
    (JsPath \ "requiredDuringSchedulingIgnoredDuringExecution").formatMaybeEmptyList[Pod.Affinity.PodAffinityTerm] and
    (JsPath \ "preferredDuringSchedulingIgnoredDuringExecution").formatMaybeEmptyList[Pod.Affinity.WeightedPodAffinityTerm]
  )(Pod.Affinity.PodAffinity.apply, p => (p.requiredDuringSchedulingIgnoredDuringExecution, p.preferredDuringSchedulingIgnoredDuringExecution))

  implicit lazy val podAntiAffinityFormat: Format[Pod.Affinity.PodAntiAffinity] = (
    (JsPath \ "requiredDuringSchedulingIgnoredDuringExecution").formatMaybeEmptyList[Pod.Affinity.PodAffinityTerm] and
    (JsPath \ "preferredDuringSchedulingIgnoredDuringExecution").formatMaybeEmptyList[Pod.Affinity.WeightedPodAffinityTerm]
  )(Pod.Affinity.PodAntiAffinity.apply, p => (p.requiredDuringSchedulingIgnoredDuringExecution, p.preferredDuringSchedulingIgnoredDuringExecution))


  implicit lazy val affinityFormat : Format[Pod.Affinity] = Json.format[Pod.Affinity]

  implicit val hostAliasFmt: Format[Pod.HostAlias] = Json.format[Pod.HostAlias]
  implicit val podDNSConfigOptionFmt: Format[Pod.DNSConfigOption] = Json.format[Pod.DNSConfigOption]
  implicit val podDNSConfigFmt: Format[Pod.DNSConfig] = (
      (JsPath \ "nameservers").formatMaybeEmptyList[String] and
      (JsPath \ "options").formatMaybeEmptyList[Pod.DNSConfigOption] and
      (JsPath \ "searches").formatMaybeEmptyList[String]
  )(Pod.DNSConfig.apply, p => (p.nameservers, p.options, p.searches))

  // the following ugliness is to do with the Kubernetes pod spec schema expanding until it takes over the entire universe,
  // which has finally necessitated a hack to get around Play Json limitations supporting case classes with > 22 members
  // (see e.g. https://stackoverflow.com/questions/28167971/scala-case-having-22-fields-but-having-issue-with-play-json-in-scala-2-11-5)

  val podSpecPartOneFormat: OFormat[(List[Container], List[Container], List[Volume], RestartPolicy.Value, Option[Int], Option[Int], DNSPolicy.Value, Map[String, String], String, String, Boolean, List[LocalObjectReference], Option[Pod.Affinity], List[Pod.Toleration], Option[PodSecurityContext])] = (
      (JsPath \ "containers").format[List[Container]] and
      (JsPath \ "initContainers").formatMaybeEmptyList[Container] and
      (JsPath \ "volumes").formatMaybeEmptyList[Volume] and
      (JsPath \ "restartPolicy").formatEnum(RestartPolicy)(Some(RestartPolicy.Always)) and
      (JsPath \ "terminationGracePeriodSeconds").formatNullable[Int] and
      (JsPath \ "activeDeadlineSeconds").formatNullable[Int] and
      (JsPath \ "dnsPolicy").formatEnum(DNSPolicy)(Some(DNSPolicy.ClusterFirst)) and
      (JsPath \ "nodeSelector").formatMaybeEmptyMap[String] and
      (JsPath \ "serviceAccountName").formatMaybeEmptyString() and
      (JsPath \ "nodeName").formatMaybeEmptyString() and
      (JsPath \ "hostNetwork").formatMaybeEmptyBoolean() and
      (JsPath \ "imagePullSecrets").formatMaybeEmptyList[LocalObjectReference] and
      (JsPath \ "affinity").formatNullable[Pod.Affinity] and
      (JsPath \ "tolerations").formatMaybeEmptyList[Pod.Toleration] and
      (JsPath \ "securityContext").formatNullable[PodSecurityContext]
     ).tupled

  val podSpecPartTwoFormat: OFormat[(Option[String], List[Pod.HostAlias], Option[Boolean], Option[Boolean], Option[Boolean], Option[Int], Option[String], Option[String], Option[String], Option[Pod.DNSConfig], Option[Boolean])] = (
      (JsPath \ "hostname").formatNullable[String] and
      (JsPath \ "hostAliases").formatMaybeEmptyList[Pod.HostAlias] and
      (JsPath \ "hostPID").formatNullable[Boolean] and
      (JsPath \ "hostIPC").formatNullable[Boolean] and
      (JsPath \ "automountServiceAccountToken").formatNullable[Boolean] and
      (JsPath \ "priority").formatNullable[Int] and
      (JsPath \ "priorityClassName").formatNullable[String] and
      (JsPath \ "schedulerName").formatNullable[String] and
      (JsPath \ "subdomain").formatNullable[String] and
      (JsPath \ "dnsConfig").formatNullable[Pod.DNSConfig] and
      (JsPath \ "shareProcessNamespace").formatNullable[Boolean]
  ).tupled

  def fromTuples(
    partOne: (scala.List[Container], scala.List[Container], scala.List[Volume], RestartPolicy.Value, Option[Int], Option[Int], DNSPolicy.Value, Map[String, String], String, String, Boolean, scala.List[LocalObjectReference], Option[Pod.Affinity], scala.List[Pod.Toleration], Option[PodSecurityContext]),
    partTwo: (Option[String], scala.List[Pod.HostAlias], Option[Boolean], Option[Boolean], Option[Boolean], Option[Int], Option[String], Option[String], Option[String], Option[Pod.DNSConfig], Option[Boolean])
  ): Pod.Spec = {
    val (conts, initConts, vols, rpol, tgps, adls, dnspol, nodesel, svcac, node, hnet, ips, aff, tol, psc) = partOne
    val (host, aliases, pid, ipc, asat, prio, prioc, sched, subd, dnsc, spn) = partTwo

    Pod.Spec(conts, initConts, vols, rpol, tgps, adls, dnspol, nodesel, svcac, node, hnet, ips, aff, tol, psc, host, aliases, pid, ipc, asat, prio, prioc, sched, subd, dnsc, spn)
  }

  implicit val podSpecFmt: Format[Pod.Spec] = (
      podSpecPartOneFormat and podSpecPartTwoFormat
  ).apply[Pod.Spec]({
    (partOne, partTwo) => fromTuples(partOne, partTwo)
  }, { (s: Pod.Spec) => (
      ( s.containers,
        s.initContainers,
        s.volumes,
        s.restartPolicy,
        s.terminationGracePeriodSeconds,
        s.activeDeadlineSeconds,
        s.dnsPolicy,
        s.nodeSelector,
        s.serviceAccountName,
        s.nodeName,
        s.hostNetwork,
        s.imagePullSecrets,
        s.affinity,
        s.tolerations,
        s.securityContext
      ),
      ( s.hostname,
        s.hostAliases,
        s.hostPID,
        s.hostIPC,
        s.automountServiceAccountToken,
        s.priority,
        s.priorityClassName,
        s.schedulerName,
        s.subdomain,
        s.dnsConfig,
        s.shareProcessNamespace
      ))
  }
  )

  implicit val podTemplSpecFormat: Format[Pod.Template.Spec] = Json.format[Pod.Template.Spec]
  implicit lazy val podTemplFormat : Format[Pod.Template] = (
      objFormat and
      (JsPath \ "spec").formatNullable[Pod.Template.Spec]
    ) (Pod.Template.apply, p => (p.kind, p.apiVersion, p.metadata, p.spec))

  implicit val repCtrlrSpecFormat: Format[ReplicationController.Spec] = Json.format[ReplicationController.Spec]
  implicit val repCtrlrStatusFormat: Format[ReplicationController.Status] = Json.format[ReplicationController.Status]

   implicit lazy val repCtrlrFormat: Format[ReplicationController] = (
    objFormat and
    (JsPath \ "spec").formatNullable[ReplicationController.Spec] and
    (JsPath \ "status").formatNullable[ReplicationController.Status]
  ) (ReplicationController.apply, r => (r.kind, r.apiVersion, r.metadata, r.spec, r.status))


  implicit val loadBalIngressFmt: Format[Service.LoadBalancer.Ingress] = Json.format[Service.LoadBalancer.Ingress]
  implicit val loadBalStatusFmt: Format[Service.LoadBalancer.Status] =
    (JsPath \ "ingress").formatMaybeEmptyList[Service.LoadBalancer.Ingress].
        inmap(ingress => Service.LoadBalancer.Status(ingress), (lbs:Service.LoadBalancer.Status) => lbs.ingress)

  implicit val serviceStatusFmt: Format[Service.Status] =
    (JsPath \ "loadBalancer").formatNullable[Service.LoadBalancer.Status].
        inmap(lbs=> Service.Status(lbs), (ss:Service.Status) => ss.loadBalancer)

  implicit val servicePortFmt: Format[Service.Port] = (
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "protocol").formatEnum(Protocol)(Some(Protocol.TCP)) and
    (JsPath \ "port").format[Int] and
    (JsPath \ "targetPort").formatNullable[NameablePort] and
    (JsPath \ "nodePort").formatMaybeEmptyInt()
  ) (Service.Port.apply, s => (s.name, s.protocol, s.port, s.targetPort, s.nodePort))

  implicit val serviceSpecFmt: Format[Service.Spec] = (
      (JsPath \ "ports").formatMaybeEmptyList[Service.Port] and
      (JsPath \ "selector").formatMaybeEmptyMap[String] and
      (JsPath \ "clusterIP").formatMaybeEmptyString() and
      (JsPath \ "type").formatEnum(Service.Type)(Some(Service.Type.ClusterIP)) and
      (JsPath \ "externalIPs").formatMaybeEmptyList[String] and
      (JsPath \ "externalName").formatMaybeEmptyString() and
      (JsPath \ "externalTrafficPolicy").formatNullableEnum(Service.ExternalTrafficPolicy) and
      (JsPath \ "sessionAffinity").formatEnum(Service.Affinity)(Some(Service.Affinity.None)) and
      (JsPath \ "loadBalancerIP").formatMaybeEmptyString() and
      (JsPath \ "publishNotReadyAddresses").formatMaybeEmptyBoolean()
   )(Service.Spec.apply, s => (s.ports, s. selector, s.clusterIP, s._type, s.externalIPs, s.externalName, s.externalTrafficPolicy, s.sessionAffinity, s.loadBalancerIP, s.publishNotReadyAddresses))

  implicit val serviceFmt: Format[Service] = (
     objFormat and
     (JsPath \ "spec").formatNullable[Service.Spec] and
     (JsPath \ "status").formatNullable[Service.Status]
  )(Service.apply, s => (s.kind, s.apiVersion, s.metadata, s.spec, s.status))

  implicit val endpointsAddressFmt: Format[Endpoints.Address] = Json.format[Endpoints.Address]
  implicit val endpointPortFmt: Format[Endpoints.Port] = Json.format[Endpoints.Port]
  implicit val endpointSubsetFmt: Format[Endpoints.Subset] = Json.format[Endpoints.Subset]

  implicit val endpointFmt: Format[Endpoints] = (
    objFormat and
    (JsPath \ "subsets").format[List[Endpoints.Subset]]
  )(Endpoints.apply, e => (e.kind, e.apiVersion, e.metadata, e.subsets))

  implicit val nodeSysInfoFmt: Format[Node.SystemInfo] = Json.format[Node.SystemInfo]

  implicit val nodeAddrFmt: Format[Node.Address] = (
    (JsPath \ "type").format[String] and
    (JsPath \ "address").format[String]
  )(Node.Address.apply, n => (n._type, n.address))

  implicit val nodeCondFmt: Format[Node.Condition] = (
    (JsPath \ "type").format[String] and
    (JsPath \ "status").format[String] and
    (JsPath \ "lastHeartbeatTime").formatNullable[Timestamp] and
    (JsPath \ "lastTransitionTime").formatNullable[Timestamp] and
    (JsPath \ "reason").formatNullable[String] and
    (JsPath \ "message").formatNullable[String]
  )(Node.Condition.apply, n => (n._type, n.status, n.lastHeartbeatTime, n.lastTransitionTime, n.reason, n.message))

  implicit val nodeDaemEndpFmt: Format[Node.DaemonEndpoint] = Json.format[Node.DaemonEndpoint]
  implicit val nodeDaemEndpsFmt: Format[Node.DaemonEndpoints] = Json.format[Node.DaemonEndpoints]
  implicit val nodeAttachedVolFmt: Format[Node.AttachedVolume] = Json.format[Node.AttachedVolume]

  implicit val nodeStatusFmt: Format[Node.Status] = (
    (JsPath \ "capacity").formatMaybeEmptyMap[Resource.Quantity] and
    (JsPath \ "phase").formatNullableEnum(Node.Phase) and
    (JsPath \ "conditions").formatMaybeEmptyList[Node.Condition] and
    (JsPath \ "addresses").formatMaybeEmptyList[Node.Address] and
    (JsPath \ "nodeInfo").formatNullable[Node.SystemInfo] and
    (JsPath \ "allocatable").formatMaybeEmptyMap[Resource.Quantity] and
    (JsPath \ "daemonEndpoints").formatNullable[Node.DaemonEndpoints] and
    (JsPath \ "images").formatMaybeEmptyList[Container.Image] and
    (JsPath \ "volumesInUse").formatMaybeEmptyList[String] and
    (JsPath \ "volumesAttached").formatMaybeEmptyList[Node.AttachedVolume]
  )(Node.Status.apply, n => (n.capacity, n.phase, n.conditions, n.addresses, n.nodeInfo, n.allocatable, n.daemonEndpoints, n.images, n.volumesInUse, n.volumesAttached))

  implicit val nodeTaintFmt: Format[Node.Taint] = Json.format[Node.Taint]

  implicit val nodeSpecFmt: Format[Node.Spec] =(
    (JsPath \ "podCIDR").formatMaybeEmptyString() and
    (JsPath \ "providerID").formatMaybeEmptyString() and
    (JsPath \ "unschedulable").formatMaybeEmptyBoolean() and
    (JsPath \ "externalID").formatMaybeEmptyString() and
    (JsPath \ "taints").formatMaybeEmptyList[Node.Taint]
  )(Node.Spec.apply, n => (n.podCIDR, n.providerID, n.unschedulable, n.externalID, n.taints))

  implicit val nodeFmt: Format[Node] = (
    objFormat and
    (JsPath \ "spec").formatNullable[Node.Spec] and
    (JsPath \ "status").formatNullable[Node.Status]
  )(Node.apply, n => (n.kind, n.apiVersion, n.metadata, n.spec, n.status))

  implicit val eventSrcFmt: Format[Event.Source] = Json.format[Event.Source]

  implicit val eventFmt: Format[Event] = (
    objFormat and
    (JsPath \ "involvedObject").format[ObjectReference] and
    (JsPath \ "reason").formatNullable[String] and
    (JsPath \ "message").formatNullable[String] and
    (JsPath \ "source").formatNullable[Event.Source] and
    (JsPath \ "firstTimestamp").formatNullable[Timestamp] and
    (JsPath \ "lastTimestamp").formatNullable[Timestamp] and
    (JsPath \ "count").formatNullable[Int] and
    (JsPath \ "type").formatNullable[String]
  )(Event.apply, e => (e.kind, e.apiVersion, e.metadata, e.involvedObject, e.reason, e.message, e.source, e.firstTimestamp, e.lastTimestamp, e.count, e.`type`))

  implicit val accessModeFmt: Format[PersistentVolume.AccessMode.AccessMode] = Format(enumReads(PersistentVolume.AccessMode), enumWrites(PersistentVolume.AccessMode))
  implicit val pvolPhaseFmt: Format[PersistentVolume.Phase.Phase] = Format(enumReads(PersistentVolume.Phase), enumWrites(PersistentVolume.Phase))
  implicit val reclaimPolicyFmt: Format[PersistentVolume.ReclaimPolicy.ReclaimPolicy] = Format(enumReads(PersistentVolume.ReclaimPolicy), enumWrites(PersistentVolume.ReclaimPolicy))

  implicit val perVolSpecFmt: Format[PersistentVolume.Spec] = (
      (JsPath \ "capacity").formatMaybeEmptyMap[Resource.Quantity] and
      JsPath.format[Volume.PersistentSource] and
      (JsPath \ "accessModes").formatMaybeEmptyList[PersistentVolume.AccessMode.AccessMode] and
      (JsPath \ "claimRef").formatNullable[ObjectReference] and
      (JsPath \ "persistentVolumeReclaimPolicy").formatNullableEnum(PersistentVolume.ReclaimPolicy)
  )(PersistentVolume.Spec.apply, p => (p.capacity, p.source, p.accessModes, p.claimRef, p.persistentVolumeReclaimPolicy))

  implicit val persVolStatusFmt: Format[PersistentVolume.Status] = (
      (JsPath \ "phase").formatNullableEnum(PersistentVolume.Phase) and
      (JsPath \ "accessModes").formatMaybeEmptyList[PersistentVolume.AccessMode.AccessMode]
  )(PersistentVolume.Status.apply, p => (p.phase, p.accessModes))

  implicit val persVolFmt: Format[PersistentVolume] = (
    objFormat and
    (JsPath \ "spec").formatNullable[PersistentVolume.Spec] and
    (JsPath \ "status").formatNullable[PersistentVolume.Status]
  )(PersistentVolume.apply, p => (p.kind, p.apiVersion, p.metadata, p.spec, p.status))


  implicit val pvClaimSpecFmt: Format[PersistentVolumeClaim.Spec] = (
      (JsPath \ "accessModes").formatMaybeEmptyList[PersistentVolume.AccessMode.AccessMode] and
      (JsPath \ "resources").formatNullable[Resource.Requirements] and
      (JsPath \ "volumeName").formatNullable[String] and
      (JsPath \ "storageClassName").formatNullable[String] and
      (JsPath \ "volumeMode").formatNullableEnum(PersistentVolumeClaim.VolumeMode) and
      (JsPath \ "selector").formatNullableLabelSelector
    )(PersistentVolumeClaim.Spec.apply, p => (p.accessModes, p.resources, p.volumeName, p.storageClassName, p.volumeMode, p.selector))

  implicit val pvClaimStatusFmt: Format[PersistentVolumeClaim.Status] = (
      (JsPath \ "phase").formatNullableEnum(PersistentVolume.Phase) and
      (JsPath \ "accessModes").formatMaybeEmptyList[PersistentVolume.AccessMode.AccessMode]
    )(PersistentVolumeClaim.Status.apply, p => (p.phase, p.accessModes))

  implicit val pvcFmt: Format[PersistentVolumeClaim] =  (
    objFormat and
      (JsPath \ "spec").formatNullable[PersistentVolumeClaim.Spec] and
      (JsPath \ "status").formatNullable[PersistentVolumeClaim.Status]
    )(PersistentVolumeClaim.apply, p => (p.kind, p.apiVersion, p.metadata, p.spec, p.status))


  implicit val configMapFmt: Format[ConfigMap] = (
    objFormat and
    (JsPath \ "data").formatMaybeEmptyMap[String]
  )(ConfigMap.apply,  c => (c.kind, c.apiVersion, c.metadata, c.data))

  implicit val svcAccountFmt: Format[ServiceAccount] = (
    objFormat and
    (JsPath \ "secrets").formatMaybeEmptyList[ObjectReference] and
    (JsPath \ "imagePullSecrets").formatMaybeEmptyList[LocalObjectReference]
  )(ServiceAccount.apply, s => (s.kind, s.apiVersion, s.metadata, s.secrets, s.imagePullSecrets))

  implicit val base64Format: Format[Array[Byte]] =
      JsPath.format[String].inmap(s => Base64.decodeBase64(s), (bytes: Array[Byte]) => Base64.encodeBase64String(bytes))

  implicit val mapStringByteArrayFormat: Format[Map[String,Array[Byte]]] =
    JsPath.format[Map[String,String]]
      .inmap(_.map({case (k,v) => k -> Base64.decodeBase64(v.getBytes)}),
        (map: Map[String, Array[Byte]]) => map.map({case (k,v) => k -> Base64.encodeBase64String(v)}))

  implicit val secretFmt: Format[Secret] = (
    objFormat and
    (JsPath \ "data").formatMaybeEmptyByteArrayMap and
    (JsPath \ "type").formatMaybeEmptyString()
  )(Secret.apply, s => (s.kind, s.apiVersion, s.metadata, s.data, s.`type`))

  implicit val limitRangeItemTypeFmt: Format[LimitRange.ItemType.Type] = enumFormat(LimitRange.ItemType)

  implicit val limitRangeItemFmt: Format[LimitRange.Item] = (
     (JsPath \ "type").formatNullableEnum(LimitRange.ItemType) and
     (JsPath \ "max").formatMaybeEmptyMap[Resource.Quantity] and
     (JsPath \ "min").formatMaybeEmptyMap[Resource.Quantity] and
     (JsPath \ "default").formatMaybeEmptyMap[Resource.Quantity] and
     (JsPath \ "defaultRequest").formatMaybeEmptyMap[Resource.Quantity] and
     (JsPath \ "maxLimitRequestRatio").formatMaybeEmptyMap[Resource.Quantity]
  )(LimitRange.Item.apply, s => (s._type, s.max, s.min, s.default, s.defaultRequest, s.maxLimitRequestRatio))

  implicit val limitRangeSpecFmt: Format[LimitRange.Spec] =
     (JsPath \ "items").formatMaybeEmptyList[LimitRange.Item].inmap(
         items => LimitRange.Spec(items), (spec: LimitRange.Spec) => spec.items)

  implicit val limitRangeFmt: Format[LimitRange] = (
     objFormat and
     (JsPath \ "spec").formatNullable[LimitRange.Spec]
  )(LimitRange.apply, l => (l.kind, l.apiVersion, l.metadata, l.spec))

  implicit val resourceQuotaSpecFmt: Format[Resource.Quota.Spec] =
    (JsPath \ "hard").formatMaybeEmptyMap[Resource.Quantity].inmap(
        hard => Resource.Quota.Spec(hard),(spec: Resource.Quota.Spec) => spec.hard)

  implicit val resouceQuotaStatusFmt: Format[Resource.Quota.Status] = (
    (JsPath \ "hard").formatMaybeEmptyMap[Resource.Quantity] and
    (JsPath \ "used").formatMaybeEmptyMap[Resource.Quantity]
  )(Resource.Quota.Status.apply, r => (r.hard, r.used))

  implicit val resourceQuotaFmt: Format[Resource.Quota] = (
    objFormat and
    (JsPath \ "spec").formatNullable[Resource.Quota.Spec] and
    (JsPath \ "status").formatNullable[Resource.Quota.Status]
  )(Resource.Quota.apply, r => (r.kind, r.apiVersion, r.metadata, r.spec, r.status))

  implicit val podListFmt: Format[PodList] = ListResourceFormat[Pod]
  implicit val nodeListFmt: Format[NodeList] = ListResourceFormat[Node]
  implicit val configMapListFmt: Format[ConfigMapList] = ListResourceFormat[ConfigMap]
  implicit val serviceListFmt: Format[ServiceList] = ListResourceFormat[Service]
  implicit val endpointsListFmt: Format[EndpointsList] = ListResourceFormat[Endpoints]
  implicit val eventListFmt: Format[EventList] = ListResourceFormat[Event]
  implicit val namespaceListFmt: Format[NamespaceList] = ListResourceFormat[Namespace]
  implicit val replCtrlListFmt: Format[ReplicationControllerList] = ListResourceFormat[ReplicationController]
  implicit val persVolListFmt: Format[PersistentVolumeList] = ListResourceFormat[PersistentVolume]
  implicit val persVolClaimListFmt: Format[PersistentVolumeClaimList] = ListResourceFormat[PersistentVolumeClaim]
  implicit val svcAcctListFmt: Format[ServiceAccountList] = ListResourceFormat[ServiceAccount]
  implicit val resQuotaListFmt: Format[ResourceQuotaList] = ListResourceFormat[Resource.Quota]
  implicit val secretListFmt: Format[SecretList] = ListResourceFormat[Secret]
  implicit val limitRangeListFmt: Format[LimitRangeList] = ListResourceFormat[LimitRange]

  implicit val precondFmt: Format[Preconditions] =
    (JsPath \ "uid").formatMaybeEmptyString().inmap(u => Preconditions(u), p => p.uid)

  implicit val deleteOptionsFmt: Format[DeleteOptions] = (
    (JsPath \ "apiVersion").formatMaybeEmptyString() and
    (JsPath \ "kind").formatMaybeEmptyString() and
    (JsPath \ "gracePeriodSeconds").formatNullable[Int] and
    (JsPath \ "preconditions").formatNullable[Preconditions] and
    (JsPath \ "propagationPolicy").formatNullableEnum(DeletePropagation)
  )(DeleteOptions.apply, d => (d.apiVersion, d.kind, d.gracePeriodSeconds, d.preconditions, d.propagationPolicy))

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

    def watchEventFormat[T <: ObjectResource](implicit objfmt: Format[T]) : Format[WatchEvent[T]] = (
      (JsPath \ "type").formatEnum(EventType)(None) and
      (JsPath \ "object").format[T]
    )(WatchEvent.apply[T], w => (w._type, w._object))
  }

  implicit def jsonPatchOperationWrite: Writes[JsonPatchOperation.Operation] = Writes[JsonPatchOperation.Operation] { value =>
    JsObject(Map("op" -> JsString(value.op)) ++ (value match {
      case v: JsonPatchOperation.ValueOperation[_] =>
        Map(
          "path" -> JsString(v.path),
          "value" -> v.fmt.writes(v.value)
        )
      case v: JsonPatchOperation.UnaryOperation =>
        Map(
          "path" -> JsString(v.path)
        )
      case v: JsonPatchOperation.DirectionalOperation =>
        Map(
          "from" -> JsString(v.from),
          "path" -> JsString(v.path)
        )
    }))
  }

  implicit def jsonPatchWrite: Writes[JsonPatch] = Writes[JsonPatch] { value =>
    JsArray(value.operations.map(jsonPatchOperationWrite.writes)) }

  implicit val metadataPatchWrite: Writes[MetadataPatch] = Writes[MetadataPatch] { value =>
    val labels = value.labels.map { m =>
      val fields = m.map { case (k,v) => (k, JsString(v)) }.toSeq
      JsObject(fields)
    }.getOrElse(JsNull)

    val annotations = value.annotations.map { m =>
      val fields = m.map { case (k,v) => (k, JsString(v)) }.toSeq
      JsObject(fields)
    }.getOrElse(JsNull)

    val metadata = JsObject(Seq("labels" -> labels, "annotations" -> annotations))

    JsObject(Map("metadata" -> metadata))
  }
}

