package skuber.model.ac.networking

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.model.ac._
import skuber.model.networking.Ingress
import skuber.json.format._
import skuber.json.networking.format._

case class IngressSpecApplyConfig(
  defaultBackend: Option[Ingress.Backend] = None,
  ingressClassName: Option[String] = None,
  rules: Option[List[Ingress.Rule]] = None,
  tls: Option[List[Ingress.TLS]] = None
) {
  def withDefaultBackend(b: Ingress.Backend): IngressSpecApplyConfig = copy(defaultBackend = Some(b))
  def withIngressClassName(cn: String): IngressSpecApplyConfig = copy(ingressClassName = Some(cn))
  def addRule(r: Ingress.Rule): IngressSpecApplyConfig = copy(rules = Some(rules.getOrElse(Nil) :+ r))
  def withRules(r: List[Ingress.Rule]): IngressSpecApplyConfig = copy(rules = Some(r))
  def addTLS(t: Ingress.TLS): IngressSpecApplyConfig = copy(tls = Some(t :: tls.getOrElse(Nil)))
}

object IngressSpecApplyConfig {
  implicit val writes: OWrites[IngressSpecApplyConfig] = (
    (JsPath \ "defaultBackend").writeNullable[Ingress.Backend] and
    (JsPath \ "ingressClassName").writeNullable[String] and
    (JsPath \ "rules").writeNullable[List[Ingress.Rule]] and
    (JsPath \ "tls").writeNullable[List[Ingress.TLS]]
  )(s => (s.defaultBackend, s.ingressClassName, s.rules, s.tls))
}

case class IngressApplyConfig(
  kind: String = "Ingress",
  apiVersion: String = "networking.k8s.io/v1beta1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[IngressSpecApplyConfig] = None
) extends ApplyConfiguration[Ingress] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): IngressApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): IngressApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): IngressApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: IngressSpecApplyConfig): IngressApplyConfig = copy(spec = Some(s))
}

object IngressApplyConfig {
  def apply(name: String): IngressApplyConfig =
    IngressApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[IngressApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[IngressSpecApplyConfig]
  )(i => (i.kind, i.apiVersion, i.metadata, i.spec))
}
