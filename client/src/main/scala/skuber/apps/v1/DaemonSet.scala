package skuber.apps.v1

/**
 * @author David O'Riordan
 */

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{IntOrString, LabelSelector, NonCoreResourceSpecification, ObjectMeta, ObjectResource, Pod, ResourceDefinition, ResourceSpecification, Timestamp}

case class DaemonSet(val kind: String = "DaemonSet",
                     override val apiVersion: String = appsAPIVersion,
                     val metadata: ObjectMeta,
                     spec: Option[DaemonSet.Spec] = None,
                     status: Option[DaemonSet.Status] = None)
  extends ObjectResource {

  lazy val copySpec: DaemonSet.Spec = this.spec.getOrElse(new DaemonSet.Spec)

  def withTemplate(template: Pod.Template.Spec): DaemonSet = this.copy(spec = Some(copySpec.copy(template = Some(template))))

  def withLabelSelector(sel: LabelSelector): DaemonSet = this.copy(spec = Some(copySpec.copy(selector = Some(sel))))
}

object DaemonSet {

  val specification: NonCoreResourceSpecification = NonCoreResourceSpecification(apiGroup = "apps",
    version = "v1",
    scope = Scope.Namespaced,
    names = Names(plural = "daemonsets",
      singular = "daemonset",
      kind = "DaemonSet",
      shortNames = List("ds")))
  implicit val dsDef: ResourceDefinition[DaemonSet] = new ResourceDefinition[DaemonSet] {
    def spec: ResourceSpecification = specification
  }
  implicit val dsListDef: ResourceDefinition[DaemonSetList] = new ResourceDefinition[DaemonSetList] {
    def spec: ResourceSpecification = specification
  }

  def apply(name: String) = new DaemonSet(metadata = ObjectMeta(name = name))

  case class Spec(minReadySeconds: Int = 0,
                  selector: Option[LabelSelector] = None,
                  template: Option[Pod.Template.Spec] = None,
                  updateStrategy: Option[UpdateStrategy] = None,
                  revisionHistoryLimit: Option[Int] = None)

  object UpdateStrategyType extends Enumeration {
    type UpdateStrategyType = Value
    val OnDelete, RollingUpdate = Value
  }

  sealed trait UpdateStrategy {
    def _type: UpdateStrategyType.UpdateStrategyType

    def rollingUpdate: Option[RollingUpdate]
  }

  object UpdateStrategy {
    private[skuber] case class StrategyImpl(_type: UpdateStrategyType.UpdateStrategyType, rollingUpdate: Option[RollingUpdate]) extends UpdateStrategy

    def apply: UpdateStrategy = StrategyImpl(_type = UpdateStrategyType.RollingUpdate, rollingUpdate = Some(RollingUpdate()))

    def apply(_type: UpdateStrategyType.UpdateStrategyType, rollingUpdate: Option[RollingUpdate]): UpdateStrategy = StrategyImpl(_type, rollingUpdate)

    def apply(rollingUpdate: RollingUpdate): UpdateStrategy = StrategyImpl(_type = UpdateStrategyType.RollingUpdate, rollingUpdate = Some(rollingUpdate))

    def unapply(strategy: UpdateStrategy): (UpdateStrategyType.UpdateStrategyType, Option[RollingUpdate]) = (strategy._type, strategy.rollingUpdate)
  }

  case class RollingUpdate(maxUnavailable: IntOrString = Left(1))

  case class Condition(_type: String,
                       status: String,
                       reason: Option[String] = None,
                       message: Option[String] = None,
                       lastTransitionTime: Option[Timestamp] = None)

  case class Status(currentNumberScheduled: Int,
                    numberMisscheduled: Int,
                    desiredNumberScheduled: Int,
                    numberReady: Int,
                    observedGeneration: Option[Long],
                    updatedNumberScheduled: Option[Int],
                    numberAvailable: Option[Int],
                    numberUnavailable: Option[Int],
                    collisionCount: Option[Long],
                    conditions: Option[List[Condition]])

  // json formatters

  import play.api.libs.json.{Json, Format, JsPath}
  import play.api.libs.functional.syntax._
  import skuber.json.format._

  implicit val condFmt: Format[Condition] = Json.format[Condition]
  implicit val rollingUpdFmt: Format[RollingUpdate] = ((JsPath \ "maxUnavailable").formatMaybeEmptyIntOrString(Left(1)).inmap(mu => RollingUpdate(mu), (ru: RollingUpdate) => ru.maxUnavailable))

  implicit val updateStrategyFmt: Format[UpdateStrategy] = (new EnumFormatter(JsPath \ "type").formatEnum(UpdateStrategyType, UpdateStrategyType.RollingUpdate.toString) and
    (JsPath \ "rollingUpdate").formatNullable[RollingUpdate]) (UpdateStrategy.apply, UpdateStrategy.unapply)

  implicit val daemonsetStatusFmt: Format[Status] = Json.format[Status]
  implicit val daemonsetSpecFmt: Format[Spec] = ((JsPath \ "minReadySeconds").formatMaybeEmptyInt() and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "template").formatNullable[Pod.Template.Spec] and
    (JsPath \ "updateStrategy").formatNullable[UpdateStrategy] and
    (JsPath \ "revisionHistoryLimit").formatNullable[Int]) (Spec.apply, spec => (spec.minReadySeconds, spec.selector, spec.template, spec.updateStrategy, spec.revisionHistoryLimit))

  implicit lazy val daemonsetFmt: Format[DaemonSet] = (objFormat and
    (JsPath \ "spec").formatNullable[Spec] and
    (JsPath \ "status").formatNullable[Status]) (DaemonSet.apply, set => (set.kind, set.apiVersion, set.metadata, set.spec, set.status))

}

