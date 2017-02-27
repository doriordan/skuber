package skuber.annotation

import skuber.annotation.NodeAffinity.{MatchExpressions, NodeSelectorTerms}

/**
  * Created by Cory Klein on 2/22/17.
  */
case class NodeAffinity(
                    requiredDuringSchedulingIgnoredDuringExecution: Option[RequiredDuringSchedulingIgnoredDuringExecution],
                    preferredDuringSchedulingIgnoredDuringExecution: Option[PreferredDuringSchedulingIgnoredDuringExecution]
                  )

object NodeAffinity {
  val ANNOTATION_NAME = "scheduler.alpha.kubernetes.io/affinity"

  type MatchExpressions = List[MatchExpression]
  def MatchExpressions(xs: MatchExpression*) = List(xs: _*)

  type NodeSelectorTerms = List[NodeSelectorTerm]
  def NodeSelectorTerms(xs: NodeSelectorTerm*) = List(xs: _*)

  def forRequiredQuery(key: String, operator: NodeAffinity.Operator.Value, values: List[String]): NodeAffinity = {
    NodeAffinity(
      Option(
        RequiredDuringSchedulingIgnoredDuringExecution(
          NodeSelectorTerms(
            NodeSelectorTerm(
              MatchExpressions(
                MatchExpression(key, operator, values)
              )
            )
          )
        )
      )
      , None)
  }

  object Operator extends Enumeration {
    type Operator = Value
    val In, NotIn, Exists, DoesNotExist, Gt, Lt = Value
  }
}

case class RequiredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms: NodeSelectorTerms)

case class PreferredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms: NodeSelectorTerms)

case class NodeSelectorTerm(matchExpressions: MatchExpressions)

case class MatchExpression(key: String, operator: NodeAffinity.Operator.Value, values: List[String])

