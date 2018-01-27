package skuber.apps

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{LabelSelector, NonCoreResourceSpecification, ObjectMeta, ObjectResource, PersistentVolumeClaim, Pod, ResourceDefinition, Scale, Timestamp}

/**
  * Created by hollinwilkins on 4/5/17.
  */
case class StatefulSet(override val kind: String ="StatefulSet",
                       override val apiVersion: String = "apps/v1beta1", // correct at k8s 1.7
                       metadata: ObjectMeta,
                       spec:  Option[StatefulSet.Spec] = None,
                       status:  Option[StatefulSet.Status] = None) extends ObjectResource
{
  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

  lazy val copySpec = this.spec.getOrElse(new StatefulSet.Spec(template = Pod.Template.Spec()))
  private val rollingUpdateStrategy = StatefulSet.UpdateStrategy(`type`=StatefulSet.UpdateStrategyType.RollingUpdate, None)
  private def rollingUpdateStrategy(partition: Int)=
    StatefulSet.UpdateStrategy(`type`=StatefulSet.UpdateStrategyType.RollingUpdate,Some(StatefulSet.RollingUpdateStrategy(partition)))

  def withReplicas(count: Int) = this.copy(spec=Some(copySpec.copy(replicas=Some(count))))
  def withServiceName(serviceName: String) = this.copy(spec=Some(copySpec.copy(serviceName=Some(serviceName))))
  def withTemplate(template: Pod.Template.Spec) = this.copy(spec=Some(copySpec.copy(template=template)))
  def withLabelSelector(sel: LabelSelector) = this.copy(spec=Some(copySpec.copy(selector=Some(sel))))
  def withRollingUpdateStrategyPartition(partition:Int) = this.copy(spec=Some(copySpec.copy(updateStrategy = Some(rollingUpdateStrategy(partition)))))
  def withVolumeClaimTemplate(claim: PersistentVolumeClaim) = {
    val spec = copySpec.withVolumeClaimTemplate(claim)
    this.copy(spec=Some(spec))
  }
}

object StatefulSet {

  val specification=NonCoreResourceSpecification (
    group=Some("apps"),
    version="v1beta1", // version as at k8s v1.7
    scope = Scope.Namespaced,
    names=Names(
      plural = "statefulsets",
      singular = "statefulset",
      kind = "StatefulSet",
      shortNames = List()
    )
  )
  implicit val stsDef = new ResourceDefinition[StatefulSet] { def spec=specification }
  implicit val stsListDef = new ResourceDefinition[StatefulSetList] { def spec=specification }

  def apply(name: String): StatefulSet = StatefulSet(metadata=ObjectMeta(name=name))

  object PodManagementPolicyType extends Enumeration {
    type PodManagementPolicyType = Value
    val OrderedReady,Parallel = Value
  }

  object UpdateStrategyType extends Enumeration {
    type UpdateStrategyType = Value
    val OnDelete,RollingUpdate = Value
  }

  case class UpdateStrategy(`type`: UpdateStrategyType.UpdateStrategyType, rollingUpdate: Option[RollingUpdateStrategy]=None)
  case class RollingUpdateStrategy(partition: Int)

  case class Spec(replicas: Option[Int] = Some(1),
                  serviceName: Option[String] = None,
                  selector: Option[LabelSelector] = None,
                  template: Pod.Template.Spec,
                  volumeClaimTemplates: List[PersistentVolumeClaim] = Nil,
                  podManagmentPolicy: Option[PodManagementPolicyType.PodManagementPolicyType] = None,
                  updateStrategy: Option[UpdateStrategy] = None,
                  revisionHistoryLimit: Option[Int] = None)
  {
    def withVolumeClaimTemplate(claim: PersistentVolumeClaim) = copy(volumeClaimTemplates = claim :: volumeClaimTemplates)
  }

  case class Condition(`type`:String,status:String,lastTransitionTime:Option[Timestamp],reason:Option[String],message:Option[String])

  case class Status(observedGeneration: Option[Int],
                    replicas: Int,
                    readyReplicas: Option[Int],
                    updatedReplicas: Option[Int],
                    currentRevision: Option[String],
                    updateRevision: Option[String],
                    collisionCount: Option[Int],
                    conditions: Option[List[Condition]])

}
