package skuber

import java.time.format.DateTimeFormatter

/**
 * @author David O'Riordan
 */
case class Pod(val kind: String = "Pod",
               override val apiVersion: String = v1,
               val metadata: ObjectMeta,
               spec: Option[Pod.Spec] = None,
               status: Option[Pod.Status] = None)
  extends ObjectResource with Limitable

object Pod {

  val specification: CoreResourceSpecification = CoreResourceSpecification(scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(plural = "pods", singular = "pod", kind = "Pod", shortNames = List("po")))
  implicit val poDef: ResourceDefinition[Pod] = new ResourceDefinition[Pod] {
    def spec: CoreResourceSpecification = specification
  }
  implicit val poListDef: ResourceDefinition[PodList] = new ResourceDefinition[PodList] {
    def spec: CoreResourceSpecification = specification
  }

  def named(name: String): Pod = Pod(metadata = ObjectMeta(name = name))

  def apply(name: String, spec: Pod.Spec): Pod = Pod(metadata = ObjectMeta(name = name), spec = Some(spec))

  case class Spec(containers: List[Container] = List(), // should have at least one member
                  initContainers: List[Container] = Nil,
                  volumes: List[Volume] = Nil,
                  restartPolicy: RestartPolicy.RestartPolicy = RestartPolicy.Always,
                  terminationGracePeriodSeconds: Option[Int] = None,
                  activeDeadlineSeconds: Option[Int] = None,
                  dnsPolicy: DNSPolicy.DNSPolicy = DNSPolicy.ClusterFirst,
                  nodeSelector: Map[String, String] = Map(),
                  serviceAccountName: String = "",
                  nodeName: String = "",
                  hostNetwork: Boolean = false,
                  imagePullSecrets: List[LocalObjectReference] = List(),
                  affinity: Option[Affinity] = None,
                  tolerations: List[Toleration] = List(),
                  securityContext: Option[PodSecurityContext] = None,
                  hostname: Option[String] = None,
                  hostAliases: List[HostAlias] = Nil,
                  hostPID: Option[Boolean] = None,
                  hostIPC: Option[Boolean] = None,
                  automountServiceAccountToken: Option[Boolean] = None,
                  priority: Option[Int] = None,
                  priorityClassName: Option[String] = None,
                  schedulerName: Option[String] = None,
                  subdomain: Option[String] = None,
                  dnsConfig: Option[DNSConfig] = None,
                  shareProcessNamespace: Option[Boolean] = None) {

    // a few convenience methods for fluently building out a pod spec
    def addContainer(c: Container): Spec = {
      this.copy(containers = c :: containers)
    }

    def addInitContainer(c: Container): Spec = {
      this.copy(initContainers = c :: initContainers)
    }

    def addVolume(v: Volume): Spec = {
      this.copy(volumes = v :: volumes)
    }

    def addNodeSelector(kv: (String, String)): Spec = {
      this.copy(nodeSelector = this.nodeSelector + kv)
    }

    def addImagePullSecretRef(ref: String): Spec = {
      val loref = LocalObjectReference(ref)
      this.copy(imagePullSecrets = loref :: this.imagePullSecrets)
    }

    def addToleration(tol: Toleration): Spec = {
      this.copy(tolerations = tol :: tolerations)
    }

    def withTerminationGracePeriodSeconds(gp: Int): Spec = this.copy(terminationGracePeriodSeconds = Some(gp))

    def withActiveDeadlineSeconds(ad: Int): Spec = this.copy(activeDeadlineSeconds = Some(ad))

    def withDnsPolicy(dp: DNSPolicy.DNSPolicy): Spec = this.copy(dnsPolicy = dp)

    def withNodeName(nn: String): Spec = this.copy(nodeName = nn)

    def withServiceAccountName(san: String): Spec = this.copy(serviceAccountName = san)

    def withRestartPolicy(rp: RestartPolicy.RestartPolicy): Spec = this.copy(restartPolicy = rp)

    def useHostNetwork: Spec = this.copy(hostNetwork = true)
  }

  object Phase extends Enumeration {
    type Phase = Value
    val Pending, Running, Succeeded, Failed, Unknown = Value
  }

  case class Affinity(nodeAffinity: Option[Affinity.NodeAffinity] = None,
                      podAffinity: Option[Affinity.PodAffinity] = None,
                      podAntiAffinity: Option[Affinity.PodAntiAffinity] = None)

  case object Affinity {

    object NodeSelectorOperator extends Enumeration {
      type Operator = Value
      val In, NotIn, Exists, DoesNotExist, Gt, Lt = Value
    }

    case class NodeSelectorRequirement(key: String, operator: NodeSelectorOperator.Value, values: List[String])

    type NodeSelectorRequirements = List[NodeSelectorRequirement]

    def NodeSelectorRequirements(xs: NodeSelectorRequirement*) = List(xs: _*)

    case class NodeSelectorTerm(matchExpressions: NodeSelectorRequirements = List.empty, matchFields: NodeSelectorRequirements = List.empty)

    type NodeSelectorTerms = List[NodeSelectorTerm]

    def NodeSelectorTerms(xs: NodeSelectorTerm*) = List(xs: _*)

    case class NodeAffinity(requiredDuringSchedulingIgnoredDuringExecution: Option[NodeAffinity.RequiredDuringSchedulingIgnoredDuringExecution],
                            preferredDuringSchedulingIgnoredDuringExecution: NodeAffinity.PreferredSchedulingTerms)

    case object NodeAffinity {

      case class RequiredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms: NodeSelectorTerms)

      case object RequiredDuringSchedulingIgnoredDuringExecution {

        def requiredQuery(key: String, operator: NodeSelectorOperator.Value, values: List[String]): RequiredDuringSchedulingIgnoredDuringExecution = {
          RequiredDuringSchedulingIgnoredDuringExecution(NodeSelectorTerms(NodeSelectorTerm(matchExpressions = NodeSelectorRequirements(NodeSelectorRequirement(key, operator, values)))))
        }

      }

      case class PreferredSchedulingTerm(preference: NodeSelectorTerm, weight: Int)

      object PreferredSchedulingTerm {
        def preferredQuery(weight: Int, key: String, operator: NodeSelectorOperator.Value, values: List[String]): PreferredSchedulingTerm = {
          PreferredSchedulingTerm(preference = NodeSelectorTerm(matchExpressions = NodeSelectorRequirements(NodeSelectorRequirement(key, operator, values))),
            weight = weight)
        }
      }

      type PreferredSchedulingTerms = List[PreferredSchedulingTerm]

      def PreferredSchedulingTerms(xs: PreferredSchedulingTerm*) = List(xs: _*)
    }

    case class PodAffinity(requiredDuringSchedulingIgnoredDuringExecution: List[PodAffinityTerm] = Nil,
                           preferredDuringSchedulingIgnoredDuringExecution: List[WeightedPodAffinityTerm] = Nil)

    case class PodAntiAffinity(requiredDuringSchedulingIgnoredDuringExecution: List[PodAffinityTerm] = Nil,
                               preferredDuringSchedulingIgnoredDuringExecution: List[WeightedPodAffinityTerm] = Nil)

    case class PodAffinityTerm(labelSelector: Option[LabelSelector] = None, namespaces: List[String] = Nil, topologyKey: String)

    case class WeightedPodAffinityTerm(weight: Int, podAffinityTerm: PodAffinityTerm)
  }

  case class HostAlias(ip: String, hostnames: List[String])

  case class DNSConfigOption(name: String, value: String)

  case class DNSConfig(nameservers: List[String] = Nil, options: List[DNSConfigOption] = Nil, searches: List[String] = Nil)

  case class Status(phase: Option[Phase.Phase] = None,
                    conditions: List[Condition] = Nil,
                    message: Option[String] = None,
                    reason: Option[String] = None,
                    hostIP: Option[String] = None,
                    podIP: Option[String] = None,
                    startTime: Option[Timestamp] = None,
                    containerStatuses: List[Container.Status] = Nil,
                    initContainerStatuses: List[Container.Status] = Nil,
                    qosClass: Option[String] = None,
                    nominatedNodeName: Option[String] = None)

  case class Condition(_type: String = "Ready",
                       status: String,
                       reason: Option[String] = None,
                       message: Option[String] = None,
                       lastProbeTime: Option[Timestamp] = None,
                       lastTransitionTime: Option[Timestamp] = None)

  case class Template(kind: String = "PodTemplate",
                      override val apiVersion: String = v1,
                      metadata: ObjectMeta = ObjectMeta(),
                      spec: Option[Template.Spec] = None)
    extends ObjectResource {
    def withResourceVersion(version: String): Template = this.copy(metadata = metadata.copy(resourceVersion = version))

    def addLabel(label: Tuple2[String, String]): Template = this.copy(metadata = metadata.copy(labels = metadata.labels + label))

    def addLabel(label: String): Template = addLabel(label -> "")

    def addLabels(newLabels: Map[String, String]): Template = this.copy(metadata = metadata.copy(labels = metadata.labels ++ newLabels))

    def addAnnotation(anno: Tuple2[String, String]): Template = this.copy(metadata = metadata.copy(annotations = metadata.annotations + anno))

    def addAnnotation(anno: String): Template = addAnnotation(anno -> "")

    def addAnnotations(newAnnos: Map[String, String]): Template = this.copy(metadata = metadata.copy(annotations = metadata.annotations ++ newAnnos))

    def withTemplateSpec(spec: Template.Spec): Template = this.copy(spec = Some(spec))

    def withPodSpec(podSpec: Pod.Spec): Template = this.copy(spec = Some(Pod.Template.Spec(spec = Some(podSpec))))
  }

  object Template {

    val specification: CoreResourceSpecification = CoreResourceSpecification(scope = ResourceSpecification.Scope.Namespaced,
      names = ResourceSpecification.Names(plural = "podtemplates",
        singular = "podtemplate",
        kind = "PodTemplate",
        shortNames = Nil))
    implicit val ptDef: ResourceDefinition[Template] = new ResourceDefinition[Pod.Template] {
      def spec: ResourceSpecification = specification
    }
    implicit val ptListDef: ResourceDefinition[PodTemplateList] = new ResourceDefinition[PodTemplateList] {
      def spec: ResourceSpecification = specification
    }

    def named(name: String): Pod.Template = Pod.Template(metadata = ObjectMeta(name = name))

    case class Spec(metadata: ObjectMeta = ObjectMeta(),
                    spec: Option[Pod.Spec] = None) {

      private def updateSpec(f: Pod.Spec => Pod.Spec) = {
        val newPodSpec = f(this.spec.getOrElse(Pod.Spec(Nil)))
        this.copy(spec = Some(newPodSpec))
      }

      def addLabel(label: (String, String)): Spec = this.copy(metadata = metadata.copy(labels = metadata.labels + label))

      def addLabels(newLabels: Map[String, String]): Spec = this.copy(metadata = metadata.copy(labels = metadata.labels ++ newLabels))

      def addAnnotation(anno: (String, String)): Spec = this.copy(metadata = metadata.copy(annotations = metadata.annotations + anno))

      def addAnnotations(newAnnos: Map[String, String]): Spec = this.copy(metadata = metadata.copy(annotations = metadata.annotations ++ newAnnos))

      def addContainer(container: Container): Spec = updateSpec(_.addContainer(container))

      def addInitContainer(container: Container): Spec = updateSpec(_.addInitContainer(container))

      def addVolume(v: Volume): Spec = updateSpec(_.addVolume(v))

      def withServiceAccountName(san: String): Spec = updateSpec(_.withServiceAccountName(san))

      def withRestartPolicy(rp: RestartPolicy.RestartPolicy): Spec = updateSpec(_.withRestartPolicy(rp))


      def withPodSpec(spec: Pod.Spec): Spec = this.copy(spec = Some(spec))
    }

    object Spec {
      def named(name: String): Spec = Spec(metadata = ObjectMeta(name = name))
    }
  }

  sealed trait Toleration

  case class EqualToleration(key: String, value: Option[String] = None, effect: Option[TolerationEffect] = None, tolerationSeconds: Option[Int] = None) extends Toleration

  case class ExistsToleration(key: Option[String] = None, effect: Option[TolerationEffect] = None, tolerationSeconds: Option[Int] = None) extends Toleration

  sealed trait TolerationEffect {
    val name: String
  }

  object TolerationEffect {
    case object NoSchedule extends TolerationEffect {
      override val name: String = "NoSchedule"
    }

    case object PreferNoSchedule extends TolerationEffect {
      override val name: String = "PreferNoSchedule"
    }

    case object NoExecute extends TolerationEffect {
      override val name: String = "NoExecute"
    }
  }

  case class LogQueryParams(containerName: Option[String] = None,
                            follow: Option[Boolean] = None,
                            limitBytes: Option[Int] = None,
                            pretty: Option[Boolean] = None,
                            previous: Option[Boolean] = None,
                            sinceSeconds: Option[Int] = None,
                            sinceTime: Option[Timestamp] = None,
                            tailLines: Option[Int] = None,
                            timestamps: Option[Boolean] = None) {
    lazy val asOptionalsMap: Map[String, Option[String]] = Map("container" -> containerName,
      "follow" -> follow.map(_.toString),
      "limitBytes" -> limitBytes.map(_.toString),
      "pretty" -> pretty.map(_.toString),
      "previous" -> previous.map(_.toString),
      "sinceSeconds" -> sinceSeconds.map(_.toString),
      "sinceTime" -> sinceTime.map(_.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
      "tailLines" -> tailLines.map(_.toString),
      "timestamps" -> timestamps.map(_.toString))

    lazy val asMap: Map[String, String] = asOptionalsMap.collect {
      case (key, Some(value)) => key -> value
    }
  }
}
