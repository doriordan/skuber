package skuber.json.annotation

import play.api.libs.json._
import skuber.annotation._
import play.api.libs.functional.syntax._
import skuber.annotation.NodeAffinity.{MatchExpressions, NodeSelectorTerms}
import skuber.json.format._ // reuse some core formatters


/**
 * Created by Cory Klein on 2/22/17.
 */
package object format {

  implicit val nodeAffinityOperatorFormat: Format[NodeAffinity.Operator.Operator] = Json.formatEnum(NodeAffinity.Operator)

  implicit val matchExpressionFormat: Format[MatchExpression] = ((JsPath \ "key").formatMaybeEmptyString() and
    (JsPath \ "operator").formatEnum(NodeAffinity.Operator) and
    (JsPath \ "values").formatMaybeEmptyList[String]) (MatchExpression.apply, m => (m.key, m.operator, m.values))

  implicit val nodeSelectorTermFormat: Format[NodeSelectorTerm] = ((JsPath \ "matchExpressions").format[MatchExpressions].inmap(matchExpressions => NodeSelectorTerm(matchExpressions), (nst: NodeSelectorTerm) => nst.matchExpressions))

  implicit val requiredDuringSchedulingIgnoredDuringExecutionFormat: Format[RequiredDuringSchedulingIgnoredDuringExecution] = ((JsPath \ "nodeSelectorTerms").format[NodeSelectorTerms].inmap(nodeSelectorTerms => RequiredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms), (rdside: RequiredDuringSchedulingIgnoredDuringExecution) => rdside.nodeSelectorTerms))

  implicit val preferredDuringSchedulingIgnoredDuringExecutionFormat: Format[PreferredDuringSchedulingIgnoredDuringExecution] = ((JsPath \ "nodeSelectorTerms").format[NodeSelectorTerms].inmap(nodeSelectorTerms => PreferredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms), (pdside: PreferredDuringSchedulingIgnoredDuringExecution) => pdside.nodeSelectorTerms))
}
