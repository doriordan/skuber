package skuber.model

case class Selector(matchLabels: Option[Map[String, String]] = None, matchExpressions: Option[List[MatchExpression]] = None)
