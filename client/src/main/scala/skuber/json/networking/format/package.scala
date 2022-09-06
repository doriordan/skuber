package skuber.json.networking

/**
 * @author David O'Riordan
 *
 *         Implicit JSON formatters for the extensions API objects
 */

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.networking.Ingress.Backend
import skuber.json.format._
import skuber.networking._

package object format {

  implicit val ingressBackendFmt: Format[Ingress.Backend] = Json.format[Ingress.Backend]

  implicit val ingressPathFmt: Format[Ingress.Path] = ((JsPath \ "path").formatMaybeEmptyString() and
    (JsPath \ "backend").format[Backend]) (Ingress.Path.apply, i => (i.path, i.backend))


  implicit val ingressHttpRuledFmt: Format[Ingress.HttpRule] = Json.format[Ingress.HttpRule]
  implicit val ingressRuleFmt: Format[Ingress.Rule] = Json.format[Ingress.Rule]
  implicit val ingressTLSFmt: Format[Ingress.TLS] = Json.format[Ingress.TLS]

  implicit val ingressSpecFormat: Format[Ingress.Spec] = ((JsPath \ "backend").formatNullable[Ingress.Backend] and
    (JsPath \ "rules").formatMaybeEmptyList[Ingress.Rule] and
    (JsPath \ "tls").formatMaybeEmptyList[Ingress.TLS]) (Ingress.Spec.apply, i => (i.backend, i.rules, i.tls))

  implicit val ingrlbingFormat: Format[Ingress.Status.LoadBalancer.Ingress] =
    Json.format[Ingress.Status.LoadBalancer.Ingress]

  implicit val ingrlbFormat: Format[Ingress.Status.LoadBalancer] = ((JsPath \ "ingress").formatMaybeEmptyList[Ingress.Status.LoadBalancer.Ingress].inmap(ings => Ingress.Status.LoadBalancer(ings),
    lb => lb.ingress))

  implicit val ingressStatusFormat: OFormat[Ingress.Status] = Json.format[Ingress.Status]

  implicit lazy val ingressFormat: Format[Ingress] = (objFormat and
    (JsPath \ "spec").formatNullable[Ingress.Spec] and
    (JsPath \ "status").formatNullable[Ingress.Status]
    ) (Ingress.apply, i => (i.kind, i.apiVersion, i.metadata, i.spec, i.status))

  implicit val ingressListFmt: Format[IngressList] = ListResourceFormat[Ingress]
}
