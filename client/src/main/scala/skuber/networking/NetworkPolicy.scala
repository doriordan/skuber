package skuber.networking

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{LabelSelector, NameablePort, NonCoreResourceSpecification, ObjectMeta, ObjectResource, Pod, Protocol, ResourceDefinition, ResourceSpecification}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath}
import skuber.json.format.{ListResourceFormat, enumFormatMethods, intOrStringFormat, jsPath2LabelSelFormat, maybeEmptyFormatMethods, objFormat}

/**
 * @author David O'Riordan
 *         This supports NetworkPolicy on Kubernetes V1.7+ (earlier beta version of this resource type not supported)
 */
case class NetworkPolicy(val kind: String = "NetworkPolicy",
                         override val apiVersion: String = "networking.k8s.io/v1",
                         val metadata: ObjectMeta,
                         spec: Option[NetworkPolicy.Spec] = None) extends ObjectResource {
  private def specSelectingAllPods = Some(NetworkPolicy.Spec(podSelector = LabelSelector()))

  private def fallbackToSelectingAllPods = this.copy(spec = this.spec.orElse(specSelectingAllPods))

  def inNamespace(namespace: String): NetworkPolicy = this.copy(metadata = this.metadata.copy(namespace = namespace))

  def withSpec(spec: NetworkPolicy.Spec): NetworkPolicy = this.copy(spec = Some(spec))

  def selectAllPods: NetworkPolicy = this.copy(spec = spec.map(s => s.copy(podSelector = LabelSelector())).orElse(specSelectingAllPods))

  def selectPods(podSelector: LabelSelector): NetworkPolicy =
    this.copy(spec = spec.map(s => s.copy(podSelector = podSelector)).orElse(Some(NetworkPolicy.Spec(podSelector = podSelector))))

  def allowIngress(ingressRule: NetworkPolicy.IngressRule): NetworkPolicy =
    fallbackToSelectingAllPods.copy(spec = spec.map(s => s.copy(ingress = ingressRule :: s.ingress)))

  // Note: policy types and egress only supported on v1.8+
  def applyIngressPolicy: NetworkPolicy = fallbackToSelectingAllPods.copy(spec = spec.map(s => s.copy(policyTypes = "Ingress" :: s.policyTypes)))

  def applyEgressPolicy: NetworkPolicy = fallbackToSelectingAllPods.copy(spec = spec.map(s => s.copy(policyTypes = "Egress" :: s.policyTypes)))

  def allowEgress(egressRule: NetworkPolicy.EgressRule): NetworkPolicy =
    fallbackToSelectingAllPods.copy(spec = spec.map(s => s.copy(egress = egressRule :: s.egress)))
}

object NetworkPolicy {

  def apply(name: String): NetworkPolicy = NetworkPolicy(metadata = ObjectMeta(name = name))

  def named(name: String): NetworkPolicy = apply(name)

  // Some potentially useful default network policies (as written for v1.8+)

  /*
   * selects all pods and applies an ingress policy with no ingress rules, which denies any ingress to any pod
   */
  def denyAllIngress(name: String, namespace: skuber.Namespace = skuber.Namespace.default): NetworkPolicy =
    NetworkPolicy
      .named(name)
      .inNamespace(namespace.name)
      .selectAllPods
      .applyIngressPolicy

  /*
   * create an ingress network policy for all pods, with  an "empty" ingress rule which effectively whitelists all ingresses
   */
  def allowAllIngress(name: String, namespace: skuber.Namespace = skuber.Namespace.default): NetworkPolicy =
    NetworkPolicy
      .named(name)
      .inNamespace(namespace.name)
      .selectAllPods
      .applyIngressPolicy
      .allowIngress(IngressRule())

  /*
   * selects all pods and applies an egress policy with no whitelisted egresses, which denies egress from any pod
   */
  def denyAllEgress(name: String, namespace: skuber.Namespace = skuber.Namespace.default): NetworkPolicy =
    NetworkPolicy
      .named(name)
      .inNamespace(namespace.name)
      .selectAllPods
      .applyEgressPolicy

  /*
   * creates an egress policy for all pods, with  an "empty" ingress rule which effectively whitelists all ingresses
   */
  def allowAllEgress(name: String, namespace: skuber.Namespace = skuber.Namespace.default): NetworkPolicy =
    NetworkPolicy
      .named(name)
      .inNamespace(namespace.name)
      .selectAllPods
      .applyEgressPolicy
      .allowEgress(EgressRule())

  /*
   * create a policy that denies all ingress and egress for all pods
   */
  def denyAllIngressAndEgress(name: String, namespace: skuber.Namespace = skuber.Namespace.default): NetworkPolicy =
    NetworkPolicy
      .named(name)
      .inNamespace(namespace.name)
      .selectAllPods
      .applyEgressPolicy
      .applyIngressPolicy

  // Kubernetes resource specification

  val specification: NonCoreResourceSpecification = NonCoreResourceSpecification(apiGroup = "networking.k8s.io",
    version = "v1",
    scope = Scope.Namespaced,
    names = Names(plural = "networkpolicies",
      singular = "networkpolicy",
      kind = "NetworkPolicy",
      shortNames = List()))

  implicit val npolDef: ResourceDefinition[NetworkPolicy] = new ResourceDefinition[NetworkPolicy] {
    def spec: ResourceSpecification = specification
  }
  implicit val npolListDef: ResourceDefinition[NetworkPolicyList] = new ResourceDefinition[NetworkPolicyList] {
    def spec: ResourceSpecification = specification
  }

  // Resource Scala Model

  case class Spec(podSelector: LabelSelector,
                   ingress: List[IngressRule] = Nil,
                   egress: List[EgressRule] = Nil,
                   policyTypes: List[String] = Nil)

  case class IngressRule(ports: List[Port] = Nil, from: List[Peer] = Nil)

  case class EgressRule(ports: List[Port] = Nil, to: List[Peer] = Nil)

  case class Port(port: NameablePort, protocol: Protocol.Value = Protocol.TCP)

  case class Peer(podSelector: Option[LabelSelector] = None,
                   namespaceSelector: Option[LabelSelector] = None,
                   ipBlock: Option[IPBlock] = None)

  case class IPBlock(cidr: String, except: List[String] = Nil)

  // Resource Json formatters

  implicit val ipBlockFmt: Format[IPBlock] = ((JsPath \ "cidr").format[String] and
      (JsPath \ "except").formatMaybeEmptyList[String]) (IPBlock.apply, i => (i.cidr, i.except))

  implicit val peerFmt: Format[Peer] = ((JsPath \ "podSelector").formatNullableLabelSelector and
      (JsPath \ "namespaceSelector").formatNullableLabelSelector and
      (JsPath \ "ipBlock").formatNullable[IPBlock]) (Peer.apply, p => (p.podSelector, p.namespaceSelector, p.ipBlock))

  implicit val portFmt: Format[Port] = ((JsPath \ "port").format[NameablePort] and
      (JsPath \ "protocol").formatEnum(Protocol, Protocol.TCP.toString)) (Port.apply, p => (p.port, p.protocol))

  implicit val ingressRuleFmt: Format[IngressRule] = ((JsPath \ "ports").formatMaybeEmptyList[Port] and
      (JsPath \ "from").formatMaybeEmptyList[Peer]) (IngressRule.apply, i => (i.ports, i.from))

  implicit val egressRuleFmt: Format[EgressRule] = ((JsPath \ "ports").formatMaybeEmptyList[Port] and
      (JsPath \ "to").formatMaybeEmptyList[Peer]) (EgressRule.apply, e => (e.ports, e.to))

  implicit val specfmt: Format[Spec] = ((JsPath \ "podSelector").formatLabelSelector and
      (JsPath \ "ingress").formatMaybeEmptyList[IngressRule] and
      (JsPath \ "egress").formatMaybeEmptyList[EgressRule] and
      (JsPath \ "policyTypes").formatMaybeEmptyList[String]) (Spec.apply, s => (s.podSelector, s.ingress, s.egress, s.policyTypes))

  implicit val networkPolicyFmt: Format[NetworkPolicy] = (objFormat and
      (JsPath \ "spec").formatNullable[Spec]) (NetworkPolicy.apply, n => (n.kind, n.apiVersion, n.metadata, n.spec))

  implicit val networkPolicyListFmt: Format[NetworkPolicyList] = ListResourceFormat[NetworkPolicy]
}

