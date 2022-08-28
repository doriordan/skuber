package skuber.ext

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{LabelSelector, NonCoreResourceSpecification, ObjectMeta, ObjectResource, Pod, ResourceDefinition, ResourceSpecification}

/**
 * @author Cory Klein
 */
case class DaemonSet(val kind: String = "DaemonSet",
                     override val apiVersion: String = extensionsAPIVersion,
                     val metadata: ObjectMeta,
                     spec: Option[DaemonSet.Spec] = None,
                     status: Option[DaemonSet.Status] = None)
  extends ObjectResource {

  lazy val copySpec: DaemonSet.Spec = this.spec.getOrElse(new DaemonSet.Spec)

  def withTemplate(template: Pod.Template.Spec): DaemonSet = this.copy(spec = Some(copySpec.copy(template = Some(template))))

  def withLabelSelector(sel: LabelSelector): DaemonSet = this.copy(spec = Some(copySpec.copy(selector = Some(sel))))
}

object DaemonSet {

  val specification: NonCoreResourceSpecification = NonCoreResourceSpecification(apiGroup = "extensions",
    version = "v1beta1",
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

  case class UpdateStrategy(`type`: Option[String] = Some("OnDelete"), rollingUpdate: Option[RollingUpdate] = None)

  case class RollingUpdate(maxUnavailable: Int = 1)

  case class Status(currentNumberScheduled: Int,
                    numberMisscheduled: Int,
                    desiredNumberScheduled: Int,
                    numberReady: Int,
                    observedGeneration: Option[Long],
                    updatedNumberScheduled: Option[Int],
                    numberAvailable: Option[Int],
                    numberUnavailable: Option[Int],
                    collisionCount: Option[Long])
}
