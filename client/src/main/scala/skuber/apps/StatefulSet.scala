package skuber.apps

import skuber.ResourceSpecification.{Names, Scope}
import skuber.ext.extensionsAPIVersion
import skuber.{LabelSelector, NonCoreResourceSpecification, ObjectMeta, ObjectResource, PersistentVolumeClaim, Pod, ResourceDefinition}

/**
  * Created by hollinwilkins on 4/5/17.
  */
case class StatefulSet(override val kind: String ="StatefulSet",
                       override val apiVersion: String = extensionsAPIVersion,
                       metadata: ObjectMeta,
                       spec:  Option[StatefulSet.Spec] = None,
                       status:  Option[StatefulSet.Status] = None) extends ObjectResource {
  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

  lazy val copySpec = this.spec.getOrElse(new StatefulSet.Spec)

  def withReplicas(count: Int) = this.copy(spec=Some(copySpec.copy(replicas=Some(count))))
  def withServiceName(serviceName: String) = this.copy(spec=Some(copySpec.copy(serviceName=Some(serviceName))))
  def withTemplate(template: Pod.Template.Spec) = this.copy(spec=Some(copySpec.copy(template=Some(template))))
  def withLabelSelector(sel: LabelSelector) = this.copy(spec=Some(copySpec.copy(selector=Some(sel))))

  def withVolumeClaimTemplate(claim: PersistentVolumeClaim) = {
    val spec = copySpec.withVolumeClaimTemplate(claim)
    this.copy(spec=Some(spec))
  }
}

object StatefulSet {

  val specification=NonCoreResourceSpecification (
    group=Some("apps"),
    version="v1beta1",
    scope = Scope.Namespaced,
    names=Names(
      plural = "statefulset",
      singular = "statefulset",
      kind = "StatefulSet",
      shortNames = List("deploy")
    )
  )
  implicit val stsDef = new ResourceDefinition[StatefulSet] { def spec=specification }
  implicit val stsListDef = new ResourceDefinition[StatefulSetList] { def spec=specification }

  def apply(name: String): StatefulSet =
    StatefulSet(metadata=ObjectMeta(name=name))

  case class Spec(replicas: Option[Int] = Some(1),
                  serviceName: Option[String] = None,
                  selector: Option[LabelSelector] = None,
                  template: Option[Pod.Template.Spec] = None,
                  volumeClaimTemplates: List[PersistentVolumeClaim] = Nil) {
    def withVolumeClaimTemplate(claim: PersistentVolumeClaim) = copy(volumeClaimTemplates = claim :: volumeClaimTemplates)
  }

  case class Status(observedGeneration: Int,
                    replicas: Int)
}
