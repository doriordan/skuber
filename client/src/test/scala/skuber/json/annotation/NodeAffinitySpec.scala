package skuber.json.annotation

import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.annotation.NodeAffinity.{MatchExpressions, NodeSelectorTerms}
import skuber.annotation._
import skuber.json.annotation.format._

/**
  * Created by Cory Klein on 2/22/17.
  */
class NodeAffinitySpec extends Specification {
  "This is a unit specification for the skuber formatter for k8s NodeAffinity.\n ".txt

  private val operator = NodeAffinity.Operator.NotIn
  private val operatorString = "\"NotIn\""

  private val matchExpression = MatchExpression("disk", NodeAffinity.Operator.NotIn, List("hdd"))
  private val matchExpressionString =
    s"""{
       |  "key": "disk",
       |  "operator": $operatorString,
       |  "values": ["hdd"]
       |}""".stripMargin

  private val matchExpressions: MatchExpressions = MatchExpressions(matchExpression)
  private val matchExpressionsString =
    s"""
       |[ $matchExpressionString ]
     """.stripMargin

  private val nodeSelectorTerm = NodeSelectorTerm(matchExpressions)
  private val nodeSelectorTermString =
    s"""
       |{
       |  "matchExpressions": $matchExpressionsString
       |}
     """.stripMargin

  private val nodeSelectorTerms: NodeSelectorTerms = NodeSelectorTerms(nodeSelectorTerm)
  private val nodeSelectorTermsString =
    s"""
       |[ $nodeSelectorTermString ]
     """.stripMargin

  private val requiredDuringSchedulingIgnoredDuringExecution = RequiredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms)
  private val requiredDuringSchedulingIgnoredDuringExecutionString =
    s"""
       |{
       |  "nodeSelectorTerms": $nodeSelectorTermsString
       |}
     """.stripMargin

  private val preferredDuringSchedulingIgnoredDuringExecution = PreferredDuringSchedulingIgnoredDuringExecution(nodeSelectorTerms)
  private val preferredDuringSchedulingIgnoredDuringExecutionString =
    s"""
       |{
       |  "nodeSelectorTerms": $nodeSelectorTermsString
       |}
     """.stripMargin


  "An Operator can be written to json and back in \n" >> {
    val expectedJson = Json.parse("\"" + operator.toString + "\"")
    val serializedOperatorJson = Json.toJson(operator)
    serializedOperatorJson mustEqual expectedJson

    val deserializedOperator = Json.fromJson[NodeAffinity.Operator.Value](serializedOperatorJson).get
    deserializedOperator mustEqual operator
  }

  "A MatchExpression can be written to json and back in \n" >> {
    val expectedJson = Json.parse(matchExpressionString)
    val serializedMatchExpressionJson = Json.toJson(matchExpression)
    serializedMatchExpressionJson mustEqual expectedJson

    val deserializedMatchExpression = Json.fromJson[MatchExpression](serializedMatchExpressionJson).get
    deserializedMatchExpression mustEqual matchExpression
  }

  "A MatchExpressions can be written to json and back in \n" >> {
    val expectedJson = Json.parse(matchExpressionsString)
    val serializedMatchExpressionsJson = Json.toJson(matchExpressions)
    serializedMatchExpressionsJson mustEqual expectedJson

    val deserializedMatchExpressions = Json.fromJson[MatchExpressions](serializedMatchExpressionsJson).get
    deserializedMatchExpressions mustEqual matchExpressions
  }

  "A NodeSelectorTerm can be written to json and back in \n" >> {
    val expectedJson = Json.parse(nodeSelectorTermString)
    val serializedNodeSelectorTermJson = Json.toJson(nodeSelectorTerm)
    serializedNodeSelectorTermJson mustEqual expectedJson

    val deserializedNodeSelectorTerm = Json.fromJson[NodeSelectorTerm](serializedNodeSelectorTermJson).get
    deserializedNodeSelectorTerm mustEqual nodeSelectorTerm
  }

  "A NodeSelectorTerms can be written to json and back in \n" >> {
    val expectedJson = Json.parse(nodeSelectorTermsString)
    val serializedNodeSelectorTermsJson = Json.toJson(nodeSelectorTerms)
    serializedNodeSelectorTermsJson mustEqual expectedJson

    val deserializedNodeSelectorTerms = Json.fromJson[NodeSelectorTerms](serializedNodeSelectorTermsJson).get
    deserializedNodeSelectorTerms mustEqual nodeSelectorTerms
  }

  "A RequiredDuringSchedulingIgnoredDuringExecution can be written to json and back in \n" >> {
    val expectedJson = Json.parse(requiredDuringSchedulingIgnoredDuringExecutionString)
    val serializedRequiredDuringSchedulingIgnoredDuringExecutionJson = Json.toJson(requiredDuringSchedulingIgnoredDuringExecution)
    serializedRequiredDuringSchedulingIgnoredDuringExecutionJson mustEqual expectedJson

    val deserializedRequiredDuringSchedulingIgnoredDuringExecution = Json.fromJson[RequiredDuringSchedulingIgnoredDuringExecution](serializedRequiredDuringSchedulingIgnoredDuringExecutionJson).get
    deserializedRequiredDuringSchedulingIgnoredDuringExecution mustEqual requiredDuringSchedulingIgnoredDuringExecution
  }

  "A PreferredDuringSchedulingIgnoredDuringExecution can be written to json and back in \n" >> {
    val expectedJson = Json.parse(preferredDuringSchedulingIgnoredDuringExecutionString)
    val serializedPreferredDuringSchedulingIgnoredDuringExecutionJson = Json.toJson(preferredDuringSchedulingIgnoredDuringExecution)
    serializedPreferredDuringSchedulingIgnoredDuringExecutionJson mustEqual expectedJson

    val deserializedPreferredDuringSchedulingIgnoredDuringExecution = Json.fromJson[PreferredDuringSchedulingIgnoredDuringExecution](serializedPreferredDuringSchedulingIgnoredDuringExecutionJson).get
    deserializedPreferredDuringSchedulingIgnoredDuringExecution mustEqual preferredDuringSchedulingIgnoredDuringExecution
  }
}
