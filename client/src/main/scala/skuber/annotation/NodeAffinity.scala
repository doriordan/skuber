package skuber.annotation

import play.api.libs.json.{Format, Json, OFormat}
import skuber.annotation.NodeAffinity.{MatchExpressions, NodeSelectorTerms, Operator}

/**
  * Created by Cory Klein on 2/22/17.
  *
  * 2017-10-05: per https://github.com/kubernetes/kubernetes/issues/44339, node affinity via annotation is not supported by default in
  * Kubernetes 1.6 or later. It should be set directly in the Pod.Spec (see PodFormatSpec for an example)
  */
case class NodeAffinity(requiredDuringSchedulingIgnoredDuringExecution: Option[RequiredDuringSchedulingIgnoredDuringExecution],
                    preferredDuringSchedulingIgnoredDuringExecution: Option[PreferredDuringSchedulingIgnoredDuringExecution])



case class RequiredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms: NodeSelectorTerms)

case class PreferredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms: NodeSelectorTerms)

case class NodeSelectorTerm(matchExpressions: MatchExpressions)

case class MatchExpression(key: String, operator: NodeAffinity.Operator.Value, values: List[String])

object NodeAffinity {
  val ANNOTATION_NAME = "scheduler.alpha.kubernetes.io/affinity"

  type MatchExpressions = List[MatchExpression]
  def MatchExpressions(xs: MatchExpression*) = List(xs: _*)

  type NodeSelectorTerms = List[NodeSelectorTerm]
  def NodeSelectorTerms(xs: NodeSelectorTerm*) = List(xs: _*)

  def forRequiredQuery(key: String, operator: NodeAffinity.Operator.Value, values: List[String]): NodeAffinity = {
    NodeAffinity(Option(RequiredDuringSchedulingIgnoredDuringExecution(NodeSelectorTerms(NodeSelectorTerm(MatchExpressions(MatchExpression(key, operator, values))))))
      , None)
  }

  object Operator extends Enumeration {
    type Operator = Value
    val In, NotIn, Exists, DoesNotExist, Gt, Lt = Value
  }
  implicit val operatorFmt: Format[Operator.Value] = Json.formatEnum(Operator)
  implicit val matchExpressionFmt: OFormat[MatchExpression] = Json.format[MatchExpression]
}
