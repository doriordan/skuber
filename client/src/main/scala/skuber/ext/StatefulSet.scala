package skuber.ext

import skuber.{LabelSelector, ObjectMeta, ObjectResource, PersistentVolumeClaim, Pod}

/**
  * Created by hollinwilkins on 4/5/17.
  */
case class StatefulSet(override val kind: String ="StatefulSet",
                       override val apiVersion: String = extensionsAPIVersion,
                       metadata: ObjectMeta,
                       spec:  Option[StatefulSet.Spec] = None,
                       status:  Option[StatefulSet.Status] = None) extends ObjectResource {
  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

  lazy val copySpec = this.spec.map(_.copy())

  def withReplicas(count: Int) = this.copy(spec=copySpec.map(_.copy(replicas=count)))
  def withTemplate(template: Pod.Template.Spec) = this.copy(spec=copySpec.map(_.copy(template=Some(template))))
  def withLabelSelector(sel: LabelSelector) = this.copy(spec=copySpec.map(_.copy(selector=Some(sel))))
}

object StatefulSet {
  case class Spec(replicas: Int = 1,
                  serviceName: String,
                  selector: Option[LabelSelector] = None,
                  template: Option[Pod.Template.Spec] = None,
                  volumeClaimTemplates: List[PersistentVolumeClaim])

  case class Status(observedGeneration: Int,
                    replicas: Int)
}
