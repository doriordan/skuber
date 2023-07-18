package skuber.json.annotation

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.json.format._
import skuber.model.annotation
import skuber.model.annotation.{MatchExpression, NodeAffinity, NodeSelectorTerm, PreferredDuringSchedulingIgnoredDuringExecution, RequiredDuringSchedulingIgnoredDuringExecution} // reuse some core formatters


/**
  * Created by Cory Klein on 2/22/17.
  */
package object format {

  implicit val nodeAffinityOperatorFormat: Format[NodeAffinity.Operator.Operator] = Format(enumReads(NodeAffinity.Operator), enumWrites)

  implicit val matchExpressionFormat: Format[MatchExpression] = (
    (JsPath \ "key").formatMaybeEmptyString() and
      (JsPath \ "operator").formatEnum(NodeAffinity.Operator) and
      (JsPath \ "values").formatMaybeEmptyList[String]
    )(MatchExpression.apply _, unlift(MatchExpression.unapply))

  implicit val nodeSelectorTermFormat: Format[NodeSelectorTerm] =
    (JsPath \ "matchExpressions").format[MatchExpressions].inmap(matchExpressions => annotation.NodeSelectorTerm(matchExpressions), (nst: NodeSelectorTerm) => nst.matchExpressions)

    implicit val requiredDuringSchedulingIgnoredDuringExecutionFormat: Format[RequiredDuringSchedulingIgnoredDuringExecution] =
      (JsPath \ "nodeSelectorTerms").format[NodeSelectorTerms].inmap(nodeSelectorTerms => annotation.RequiredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms), (rdside: RequiredDuringSchedulingIgnoredDuringExecution) => rdside.nodeSelectorTerms)

    implicit val preferredDuringSchedulingIgnoredDuringExecutionFormat: Format[PreferredDuringSchedulingIgnoredDuringExecution] =
      (JsPath \ "nodeSelectorTerms").format[NodeSelectorTerms].inmap(nodeSelectorTerms => annotation.PreferredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms), (pdside: PreferredDuringSchedulingIgnoredDuringExecution) => pdside.nodeSelectorTerms)
}
