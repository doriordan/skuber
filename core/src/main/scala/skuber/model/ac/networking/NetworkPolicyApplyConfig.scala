package skuber.model.ac.networking

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.networking.NetworkPolicy
import skuber.json.format._

case class NetworkPolicySpecApplyConfig(
  podSelector: Option[LabelSelector] = None,
  ingress: Option[List[NetworkPolicy.IngressRule]] = None,
  egress: Option[List[NetworkPolicy.EgressRule]] = None,
  policyTypes: Option[List[String]] = None
) {
  def withPodSelector(s: LabelSelector): NetworkPolicySpecApplyConfig = copy(podSelector = Some(s))
  def addIngressRule(r: NetworkPolicy.IngressRule): NetworkPolicySpecApplyConfig =
    copy(ingress = Some(r :: ingress.getOrElse(Nil)))
  def addEgressRule(r: NetworkPolicy.EgressRule): NetworkPolicySpecApplyConfig =
    copy(egress = Some(r :: egress.getOrElse(Nil)))
  def withPolicyTypes(t: List[String]): NetworkPolicySpecApplyConfig = copy(policyTypes = Some(t))
}

object NetworkPolicySpecApplyConfig {
  implicit val writes: OWrites[NetworkPolicySpecApplyConfig] = (
    (JsPath \ "podSelector").writeNullable[LabelSelector] and
    (JsPath \ "ingress").writeNullable[List[NetworkPolicy.IngressRule]] and
    (JsPath \ "egress").writeNullable[List[NetworkPolicy.EgressRule]] and
    (JsPath \ "policyTypes").writeNullable[List[String]]
  )(s => (s.podSelector, s.ingress, s.egress, s.policyTypes))
}

case class NetworkPolicyApplyConfig(
  kind: String = "NetworkPolicy",
  apiVersion: String = "networking.k8s.io/v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[NetworkPolicySpecApplyConfig] = None
) extends ApplyConfiguration[NetworkPolicy] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): NetworkPolicyApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): NetworkPolicyApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): NetworkPolicyApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: NetworkPolicySpecApplyConfig): NetworkPolicyApplyConfig = copy(spec = Some(s))
}

object NetworkPolicyApplyConfig {
  def apply(name: String): NetworkPolicyApplyConfig =
    NetworkPolicyApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[NetworkPolicyApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[NetworkPolicySpecApplyConfig]
  )(n => (n.kind, n.apiVersion, n.metadata, n.spec))
}
