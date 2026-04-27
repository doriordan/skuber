package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.json.format._

case class ServiceSpecApplyConfig(
  ports: Option[List[Service.Port]] = None,
  selector: Option[Map[String, String]] = None,
  clusterIP: Option[String] = None,
  _type: Option[Service.Type.Value] = None,
  externalIPs: Option[List[String]] = None,
  sessionAffinity: Option[Service.Affinity.Value] = None,
  loadBalancerIP: Option[String] = None,
  externalTrafficPolicy: Option[Service.ExternalTrafficPolicy.Value] = None
) {
  def withSelector(sel: Map[String, String]): ServiceSpecApplyConfig = copy(selector = Some(sel))
  def setPort(p: Service.Port): ServiceSpecApplyConfig = copy(ports = Some(List(p)))
  def setPorts(ps: List[Service.Port]): ServiceSpecApplyConfig = copy(ports = Some(ps))
  def exposeOnPort(p: Service.Port): ServiceSpecApplyConfig = copy(ports = Some(p :: ports.getOrElse(Nil)))
  def withType(t: Service.Type.Value): ServiceSpecApplyConfig = copy(_type = Some(t))
  def withClusterIP(ip: String): ServiceSpecApplyConfig = copy(clusterIP = Some(ip))
  def isHeadless: ServiceSpecApplyConfig = copy(clusterIP = Some("None"))
  def withLoadBalancerIP(ip: String): ServiceSpecApplyConfig = copy(loadBalancerIP = Some(ip))
  def withExternalTrafficPolicy(p: Service.ExternalTrafficPolicy.Value): ServiceSpecApplyConfig = copy(externalTrafficPolicy = Some(p))
  def withSessionAffinity(a: Service.Affinity.Value): ServiceSpecApplyConfig = copy(sessionAffinity = Some(a))
}

object ServiceSpecApplyConfig {
  implicit val writes: OWrites[ServiceSpecApplyConfig] = (
    (JsPath \ "ports").writeNullable[List[Service.Port]] and
    (JsPath \ "selector").writeNullable[Map[String, String]] and
    (JsPath \ "clusterIP").writeNullable[String] and
    (JsPath \ "type").writeNullable[String] and
    (JsPath \ "externalIPs").writeNullable[List[String]] and
    (JsPath \ "sessionAffinity").writeNullable[String] and
    (JsPath \ "loadBalancerIP").writeNullable[String] and
    (JsPath \ "externalTrafficPolicy").writeNullable[String]
  )(s => (s.ports, s.selector, s.clusterIP, s._type.map(_.toString), s.externalIPs, s.sessionAffinity.map(_.toString), s.loadBalancerIP, s.externalTrafficPolicy.map(_.toString)))
}

case class ServiceApplyConfig(
  kind: String = "Service",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  spec: Option[ServiceSpecApplyConfig] = None
) extends ApplyConfiguration[Service] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): ServiceApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): ServiceApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): ServiceApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def withSpec(s: ServiceSpecApplyConfig): ServiceApplyConfig = copy(spec = Some(s))
  def withSelector(sel: Map[String, String]): ServiceApplyConfig = copy(spec = Some(spec.getOrElse(ServiceSpecApplyConfig()).withSelector(sel)))
  def setPort(p: Service.Port): ServiceApplyConfig = copy(spec = Some(spec.getOrElse(ServiceSpecApplyConfig()).setPort(p)))
}

object ServiceApplyConfig {
  def apply(name: String): ServiceApplyConfig =
    ServiceApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[ServiceApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "spec").writeNullable[ServiceSpecApplyConfig]
  )(s => (s.kind, s.apiVersion, s.metadata, s.spec))
}
