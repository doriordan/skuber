package skuber.apps.v1beta1

/**
  * @author David O'Riordan
  */

import skuber.ResourceSpecification.{Names, Scope}
import skuber._

case class Deployment(
  kind: String ="Deployment",
  override val apiVersion: String = appsAPIVersion,
  metadata: ObjectMeta = ObjectMeta(),
  spec:  Option[Deployment.Spec] = None,
  status: Option[Deployment.Status] = None)
    extends ObjectResource
{
  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

  lazy val copySpec = this.spec.getOrElse(new Deployment.Spec)

  def withReplicas(count: Int) = this.copy(spec=Some(copySpec.copy(replicas=Some(count))))
  def withTemplate(template: Pod.Template.Spec) = this.copy(spec=Some(copySpec.copy(template=Some(template))))
  def withLabelSelector(sel: LabelSelector) = this.copy(spec=Some(copySpec.copy(selector=Some(sel))))

  def getPodSpec = for {
    spec <- this.spec
    template <- spec.template
    spec <- template.spec
  } yield spec

  /*
   * A common deployment upgrade scenario would be to add or upgrade a single container e.g. update to a new image version
   * This supports that by adding the specified container if one of same name not specified already, or just replacing
   * the existing one of the same name if applicable.
   * The modified Deployment can then be updated on Kubernetes to instigate the upgrade.
   */
  def updateContainer(newContainer: Container): Deployment = {
    val containers = getPodSpec map { _.containers }
    val updatedContainers = containers map { list =>
      val existing = list.find(_.name==newContainer.name)
      existing match {
        case Some(_) => list.collect {
          case c if c.name==newContainer.name => newContainer
          case c => c
        }
        case None => newContainer::list
      }
    }
    val newContainerList = updatedContainers.getOrElse(List(newContainer))
    val updatedPodSpec = getPodSpec.getOrElse(Pod.Spec())
    val newPodSpec = updatedPodSpec.copy(containers=newContainerList)
    val updatedTemplate=copySpec.template.getOrElse(Pod.Template.Spec()).copy(spec=Some(newPodSpec))

    this.copy(spec=Some(copySpec.copy(template=Some(updatedTemplate))))
  }
}

object Deployment {

  val specification=NonCoreResourceSpecification (
    apiGroup="apps",
    version="v1beta1",
    scope = Scope.Namespaced,
    names=Names(
      plural = "deployments",
      singular = "deployment",
      kind = "Deployment",
      shortNames = List("deploy")
    )
  )
  implicit val deployDef = new ResourceDefinition[Deployment] { def spec= specification }
  implicit val deployListDef =  new ResourceDefinition[DeploymentList] { def spec= specification }
  implicit val scDef = new Scale.SubresourceSpec[Deployment] { override def apiVersion = appsAPIVersion }

  def apply(name: String) = new Deployment(metadata=ObjectMeta(name=name))

  case class Spec(
    replicas: Option[Int] = Some(1),
    selector: Option[LabelSelector] = None,
    template: Option[Pod.Template.Spec] = None,
    strategy: Option[Strategy] = None,
    minReadySeconds: Int = 0) {

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
    maxSurge: IntOrString = Left(1))

  case class Status(
    replicas: Int=0,
    updatedReplicas: Int=0,
    availableReplicas: Int = 0,
    unavailableReplicas: Int = 0,
    observedGeneration: Int = 0)

  // json formatters

  import play.api.libs.functional.syntax._
  import play.api.libs.json.{Format, JsPath, Json}
  import skuber.json.format._ // reuse some core skuber json formatters

  implicit val depStatusFmt: Format[Status] = (
    (JsPath \ "replicas").formatMaybeEmptyInt() and
    (JsPath \ "updatedReplicas").formatMaybeEmptyInt() and
    (JsPath \ "availableReplicas").formatMaybeEmptyInt() and
    (JsPath \ "unavailableReplicas").formatMaybeEmptyInt() and
    (JsPath \ "observedGeneration").formatMaybeEmptyInt()
  )(Status.apply _, unlift(Status.unapply))

  implicit val rollingUpdFmt: Format[RollingUpdate] = (
    (JsPath \ "maxUnavailable").formatMaybeEmptyIntOrString(Left(1)) and
    (JsPath \ "maxSurge").formatMaybeEmptyIntOrString(Left(1))
  )(RollingUpdate.apply _, unlift(RollingUpdate.unapply))

  implicit val depStrategyFmt: Format[Strategy] =  (
    (JsPath \ "type").formatEnum(StrategyType, Some(StrategyType.RollingUpdate)) and
    (JsPath \ "rollingUpdate").formatNullable[RollingUpdate]
  )(Strategy.apply _, unlift(Strategy.unapply))

  implicit val depSpecFmt: Format[Deployment.Spec] = (
    (JsPath \ "replicas").formatNullable[Int] and
    (JsPath \ "selector").formatNullableLabelSelector and
    (JsPath \ "template").formatNullable[Pod.Template.Spec] and
    (JsPath \ "strategy").formatNullable[Deployment.Strategy] and
    (JsPath \ "minReadySeconds").formatMaybeEmptyInt()
  )(Spec.apply _, unlift(Spec.unapply))

  implicit lazy val depFormat: Format[Deployment] = (
    objFormat and
    (JsPath \ "spec").formatNullable[Spec] and
    (JsPath \ "status").formatNullable[Status]
  )(Deployment.apply _, unlift(Deployment.unapply))

  implicit val deployListFormat: Format[DeploymentList] = ListResourceFormat[Deployment]
}

