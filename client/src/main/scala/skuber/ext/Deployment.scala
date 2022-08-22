package skuber.ext

/**
 * @author David O'Riordan
 */

import skuber.{Container, IntOrString, LabelSelector, ListResource, NonCoreResourceSpecification, ObjectMeta, ObjectResource, Pod, ResourceDefinition, ResourceSpecification, Scale}
import skuber.ResourceSpecification.{Names, Scope}

case class Deployment(val kind: String = "Deployment",
                      override val apiVersion: String = extensionsAPIVersion,
                      val metadata: ObjectMeta = ObjectMeta(),
                      val spec: Option[Deployment.Spec] = None,
                      val status: Option[Deployment.Status] = None)
  extends ObjectResource {

  def withResourceVersion(version: String): Deployment = this.copy(metadata = metadata.copy(resourceVersion = version))

  lazy val copySpec: skuber.ext.Deployment.Spec = this.spec.getOrElse(new Deployment.Spec)

  def withReplicas(count: Int): Deployment = this.copy(spec = Some(copySpec.copy(replicas = Some(count))))

  def withTemplate(template: Pod.Template.Spec): Deployment = this.copy(spec = Some(copySpec.copy(template = Some(template))))

  def withLabelSelector(sel: LabelSelector): Deployment = this.copy(spec = Some(copySpec.copy(selector = Some(sel))))

  def getPodSpec: Option[Pod.Spec] = for {
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
    val containers = getPodSpec map {
      _.containers
    }
    val updatedContainers = containers map { list =>
      val existing = list.find(_.name == newContainer.name)
      existing match {
        case Some(_) => list.collect {
          case c if c.name == newContainer.name => newContainer
          case c => c
        }
        case None => newContainer :: list
      }
    }
    val newContainerList = updatedContainers.getOrElse(List(newContainer))
    val updatedPodSpec = getPodSpec.getOrElse(Pod.Spec())
    val newPodSpec = updatedPodSpec.copy(containers = newContainerList)
    val updatedTemplate: Pod.Template.Spec = copySpec.template.getOrElse(Pod.Template.Spec()).copy(spec = Some(newPodSpec))

    this.copy(spec = Some(copySpec.copy(template = Some(updatedTemplate))))
  }
}

object Deployment {

  val specification: NonCoreResourceSpecification = NonCoreResourceSpecification(apiGroup = "extensions",
    version = "v1beta1",
    scope = Scope.Namespaced,
    names = Names(plural = "deployments",
      singular = "deployment",
      kind = "Deployment",
      shortNames = List("deploy")))

  implicit val deployDef: ResourceDefinition[Deployment] = new ResourceDefinition[Deployment] {
    def spec: ResourceSpecification = specification
  }
  implicit val deployListDef: ResourceDefinition[ListResource[Deployment]] = new ResourceDefinition[ListResource[Deployment]] {
    def spec: ResourceSpecification = specification
  }
  implicit val scDef: Scale.SubresourceSpec[Deployment] = new Scale.SubresourceSpec[Deployment] {
    override def apiVersion = "apps/v1beta1"
  }

  def apply(name: String) = new Deployment(metadata = ObjectMeta(name = name))

  case class Spec(replicas: Option[Int] = Some(1),
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

    def apply: Strategy = StrategyImpl(_type = StrategyType.Recreate, rollingUpdate = None)

    def apply(_type: StrategyType.StrategyType, rollingUpdate: Option[RollingUpdate]): Strategy = StrategyImpl(_type, rollingUpdate)

    def apply(rollingUpdate: RollingUpdate): Strategy = StrategyImpl(_type = StrategyType.RollingUpdate, rollingUpdate = Some(rollingUpdate))

    def unapply(strategy: Strategy): Option[(StrategyType.StrategyType, Option[RollingUpdate])] =
      Some(strategy._type, strategy.rollingUpdate)
  }

  case class RollingUpdate(maxUnavailable: IntOrString = Left(1),
                            maxSurge: IntOrString = Left(1))

  case class Status(replicas: Int = 0,
                     updatedReplicas: Int = 0,
                     availableReplicas: Int = 0,
                     unavailableReplicas: Int = 0,
                     observedGeneration: Int = 0)
}
