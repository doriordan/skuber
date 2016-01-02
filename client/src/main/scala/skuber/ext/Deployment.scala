package skuber.ext

/**
 * @author David O'Riordan
 */

import skuber._

case class Deployment(
    val kind: String ="Deployment",
    override val apiVersion: String = extensionsAPIVersion,
    val metadata: ObjectMeta = ObjectMeta(),
    val spec: Deployment.Spec,
    val status: Option[Deployment.Status] = None)
      extends ObjectResource {
  def withReplicas(count: Int) = this.copy(spec=spec.copy(replicas=count))
}
      
object Deployment {
  
  case class Spec(
    replicas: Int = 1,
    selector: Map[String, String] = Map(),
    template: Pod.Template.Spec,
    strategy: Option[Strategy] = None,
    uniqueLabelKey: String = "") {
    
    def getStrategy: Strategy = strategy.getOrElse(Strategy.apply)
  }
    
  object StrategyType extends Enumeration {
    type StrategyType = Value
    val Recreate, RollingUpdate = Value
  }
   
  sealed trait Strategy {
      def _type: StrategyType.StrategyType
      def rollingUpdate: Option[RollingUpdate]
  }
  
  object Strategy {
    private[skuber] case class StrategyImpl(_type: StrategyType.StrategyType, rollingUpdate: Option[RollingUpdate]) extends Strategy
    def apply: Strategy = StrategyImpl(_type=StrategyType.Recreate, rollingUpdate=None)
    def apply(_type: StrategyType.StrategyType,rollingUpdate: Option[RollingUpdate]) : Strategy = StrategyImpl(_type, rollingUpdate)
    def apply(rollingUpdate: RollingUpdate) : Strategy = StrategyImpl(_type=StrategyType.RollingUpdate, rollingUpdate=Some(rollingUpdate))
    def unapply(strategy: Strategy): Option[(StrategyType.StrategyType, Option[RollingUpdate])] = 
      Some(strategy._type,strategy.rollingUpdate)
  }
      
  case class RollingUpdate(
      maxUnavailable: IntOrString = Left(1),
      maxSurge: IntOrString = Left(1),
      minReadySeconds: Int = 0)
      
  case class Status(
      replicas: Int,
      updatedReplicas: Int)
}