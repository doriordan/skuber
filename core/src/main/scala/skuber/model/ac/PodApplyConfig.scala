package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.json.format._

case class PodSpecApplyConfig(
  containers: Option[List[ContainerApplyConfig]] = None,
  initContainers: Option[List[ContainerApplyConfig]] = None,
  volumes: Option[List[Volume]] = None,
  restartPolicy: Option[RestartPolicy.RestartPolicy] = None,
  terminationGracePeriodSeconds: Option[Int] = None,
  activeDeadlineSeconds: Option[Int] = None,
  dnsPolicy: Option[DNSPolicy.DNSPolicy] = None,
  nodeSelector: Option[Map[String, String]] = None,
  serviceAccountName: Option[String] = None,
  nodeName: Option[String] = None,
  hostNetwork: Option[Boolean] = None,
  imagePullSecrets: Option[List[LocalObjectReference]] = None,
  affinity: Option[Pod.Affinity] = None,
  tolerations: Option[List[Pod.Toleration]] = None,
  topologySpreadConstraints: Option[List[Pod.TopologySpreadConstraints]] = None,
  securityContext: Option[PodSecurityContext] = None,
  hostname: Option[String] = None,
  hostAliases: Option[List[Pod.HostAlias]] = None,
  hostPID: Option[Boolean] = None,
  hostIPC: Option[Boolean] = None,
  automountServiceAccountToken: Option[Boolean] = None,
  priority: Option[Int] = None,
  priorityClassName: Option[String] = None,
  schedulerName: Option[String] = None,
  subdomain: Option[String] = None,
  dnsConfig: Option[Pod.DNSConfig] = None,
  shareProcessNamespace: Option[Boolean] = None
) {
  def addContainer(c: ContainerApplyConfig): PodSpecApplyConfig = copy(containers = Some(c :: containers.getOrElse(Nil)))
  def addInitContainer(c: ContainerApplyConfig): PodSpecApplyConfig = copy(initContainers = Some(c :: initContainers.getOrElse(Nil)))
  def addVolume(v: Volume): PodSpecApplyConfig = copy(volumes = Some(v :: volumes.getOrElse(Nil)))
  def addNodeSelector(kv: (String, String)): PodSpecApplyConfig = copy(nodeSelector = Some(nodeSelector.getOrElse(Map.empty) + kv))
  def addImagePullSecretRef(ref: String): PodSpecApplyConfig = copy(imagePullSecrets = Some(LocalObjectReference(ref) :: imagePullSecrets.getOrElse(Nil)))
  def withTerminationGracePeriodSeconds(gp: Int): PodSpecApplyConfig = copy(terminationGracePeriodSeconds = Some(gp))
  def withActiveDeadlineSeconds(ad: Int): PodSpecApplyConfig = copy(activeDeadlineSeconds = Some(ad))
  def withDnsPolicy(dp: DNSPolicy.DNSPolicy): PodSpecApplyConfig = copy(dnsPolicy = Some(dp))
  def withNodeName(nn: String): PodSpecApplyConfig = copy(nodeName = Some(nn))
  def withServiceAccountName(san: String): PodSpecApplyConfig = copy(serviceAccountName = Some(san))
  def withRestartPolicy(rp: RestartPolicy.RestartPolicy): PodSpecApplyConfig = copy(restartPolicy = Some(rp))
  def useHostNetwork: PodSpecApplyConfig = copy(hostNetwork = Some(true))
}

object PodSpecApplyConfig {

  implicit val writes: OWrites[PodSpecApplyConfig] = {
    val partOne = (
      (JsPath \ "containers").writeNullable[List[ContainerApplyConfig]] and
      (JsPath \ "initContainers").writeNullable[List[ContainerApplyConfig]] and
      (JsPath \ "volumes").writeNullable[List[Volume]] and
      (JsPath \ "restartPolicy").writeNullable[String] and
      (JsPath \ "terminationGracePeriodSeconds").writeNullable[Int] and
      (JsPath \ "activeDeadlineSeconds").writeNullable[Int] and
      (JsPath \ "dnsPolicy").writeNullable[String] and
      (JsPath \ "nodeSelector").writeNullable[Map[String, String]] and
      (JsPath \ "serviceAccountName").writeNullable[String] and
      (JsPath \ "nodeName").writeNullable[String] and
      (JsPath \ "hostNetwork").writeNullable[Boolean] and
      (JsPath \ "imagePullSecrets").writeNullable[List[LocalObjectReference]] and
      (JsPath \ "affinity").writeNullable[Pod.Affinity] and
      (JsPath \ "tolerations").writeNullable[List[Pod.Toleration]] and
      (JsPath \ "topologySpreadConstraints").writeNullable[List[Pod.TopologySpreadConstraints]] and
      (JsPath \ "securityContext").writeNullable[PodSecurityContext]
    ).tupled

    val partTwo = (
      (JsPath \ "hostname").writeNullable[String] and
      (JsPath \ "hostAliases").writeNullable[List[Pod.HostAlias]] and
      (JsPath \ "hostPID").writeNullable[Boolean] and
      (JsPath \ "hostIPC").writeNullable[Boolean] and
      (JsPath \ "automountServiceAccountToken").writeNullable[Boolean] and
      (JsPath \ "priority").writeNullable[Int] and
      (JsPath \ "priorityClassName").writeNullable[String] and
      (JsPath \ "schedulerName").writeNullable[String] and
      (JsPath \ "subdomain").writeNullable[String] and
      (JsPath \ "dnsConfig").writeNullable[Pod.DNSConfig] and
      (JsPath \ "shareProcessNamespace").writeNullable[Boolean]
    ).tupled

    OWrites[PodSpecApplyConfig] { s =>
      val p1 = partOne.writes((s.containers, s.initContainers, s.volumes, s.restartPolicy.map(_.toString), s.terminationGracePeriodSeconds, s.activeDeadlineSeconds, s.dnsPolicy.map(_.toString), s.nodeSelector, s.serviceAccountName, s.nodeName, s.hostNetwork, s.imagePullSecrets, s.affinity, s.tolerations, s.topologySpreadConstraints, s.securityContext))
      val p2 = partTwo.writes((s.hostname, s.hostAliases, s.hostPID, s.hostIPC, s.automountServiceAccountToken, s.priority, s.priorityClassName, s.schedulerName, s.subdomain, s.dnsConfig, s.shareProcessNamespace))
      p1.deepMerge(p2)
    }
  }
}

case class PodApplyConfig(
  kind: String = "Pod",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[PodSpecApplyConfig] = None
) extends ApplyConfiguration[Pod] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): PodApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): PodApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): PodApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: PodSpecApplyConfig): PodApplyConfig = copy(spec = Some(s))
}

object PodApplyConfig {
  def apply(name: String): PodApplyConfig =
    PodApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  def apply(name: String, spec: PodSpecApplyConfig): PodApplyConfig =
    PodApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))), spec = Some(spec))

  implicit val writes: OWrites[PodApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[PodSpecApplyConfig]
  )(p => (p.kind, p.apiVersion, p.metadata, p.spec))
}
