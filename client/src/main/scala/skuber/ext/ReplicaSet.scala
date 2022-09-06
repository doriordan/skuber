package skuber.ext

import skuber.ResourceSpecification.{Names, Scope}
import skuber._

/**
 * @author David O'Riordan
 */
case class ReplicaSet(val kind: String = "ReplicaSet",
                      override val apiVersion: String = extensionsAPIVersion,
                      val metadata: ObjectMeta = ObjectMeta(),
                      spec: Option[ReplicaSet.Spec] = None,
                      status: Option[ReplicaSet.Status] = None)
  extends ObjectResource {

  lazy val copySpec: ReplicaSet.Spec = this.spec.getOrElse(new ReplicaSet.Spec)

  def withResourceVersion(version: String): ReplicaSet = this.copy(metadata = metadata.copy(resourceVersion = version))

  def addLabel(label: Tuple2[String, String]): ReplicaSet = this.copy(metadata = metadata.copy(labels = metadata.labels + label))

  def addLabels(newLabels: Map[String, String]): ReplicaSet = this.copy(metadata = metadata.copy(labels = metadata.labels ++ newLabels))

  def addAnnotation(anno: Tuple2[String, String]): ReplicaSet = this.copy(metadata = metadata.copy(annotations = metadata.annotations + anno))

  def addAnnotations(annos: Map[String, String]): ReplicaSet = this.copy(metadata = metadata.copy(annotations = metadata.annotations ++ annos))

  def withReplicas(n: Int): ReplicaSet = this.copy(spec = Some(copySpec.copy(replicas = Some(n))))


  def withSelector(s: LabelSelector): ReplicaSet = this.copy(spec = Some(copySpec.copy(selector = Some(s))))

  def withSelector(s: Tuple2[String, String]): ReplicaSet = withSelector(LabelSelector(LabelSelector.IsEqualRequirement(s._1, s._2)))

  /*
   * Set the template. This will set the selector from the template labels, if they exist
   * and the selector is not yet set
   */
  def withTemplate(t: Pod.Template.Spec): ReplicaSet = {
    val withTmpl = this.copy(spec = Some(copySpec.copy(template = Some(t))))
    val withSelector = (t.metadata.labels, spec.flatMap {
      _.selector
    }) match {
      case (labels, selector) if (!labels.isEmpty && !selector.isDefined) =>
        val reqs = labels map { (label: (String, String)) =>
          LabelSelector.IsEqualRequirement(label._1, label._2)
        }
        val selector = LabelSelector(reqs.toSeq: _*)
        withTmpl.withSelector(selector)
      case _ => withTmpl
    }
    // copy template labels into RS labels, if not already set
    (metadata.labels, t.metadata.labels) match {
      case (curr, default) if (curr.isEmpty && !default.isEmpty) =>
        withSelector.addLabels(default)
      case _ => withSelector
    }
  }

  /*
   * Set the template from a given Pod spec and optional set of labels
   * If the selector isn't already set then this will generate it from the labels.
   */
  def withPodSpec(t: Pod.Spec, labels: Map[String, String] = Map()): ReplicaSet = {
    val template = new Pod.Template.Spec(metadata = ObjectMeta(labels = labels), spec = Some(t))
    withTemplate(template)
  }
}

object ReplicaSet {

  val specification: NonCoreResourceSpecification = NonCoreResourceSpecification(apiGroup = "extensions",
    version = "v1beta1",
    scope = Scope.Namespaced,
    names = Names(plural = "replicasets",
      singular = "replicaset",
      kind = "ReplicaSet",
      shortNames = List("rs")))
  implicit val rsDef: ResourceDefinition[ReplicaSet] = new ResourceDefinition[ReplicaSet] {
    def spec: ResourceSpecification = specification
  }
  implicit val rsListDef: ResourceDefinition[ReplicaSetList] = new ResourceDefinition[ReplicaSetList] {
    def spec: ResourceSpecification = specification
  }
  implicit val scDef: Scale.SubresourceSpec[ReplicaSet] = new Scale.SubresourceSpec[ReplicaSet] {
    override def apiVersion: String = "extensions/v1beta1"
  }

  def apply(name: String): ReplicaSet = ReplicaSet(metadata = ObjectMeta(name = name))

  def apply(name: String, spec: ReplicaSet.Spec): ReplicaSet =
    ReplicaSet(metadata = ObjectMeta(name = name), spec = Some(spec))

  def apply(name: String, container: Container): ReplicaSet = {
    val podSpec = Pod.Spec(containers = List(container))
    ReplicaSet(name, podSpec, Map[String, String]())
  }

  def apply(name: String,
             podSpec: Pod.Spec,
             labels: Map[String, String]): ReplicaSet = {
    val meta = ObjectMeta(name = name, labels = labels)
    ReplicaSet(metadata = meta).withPodSpec(podSpec, labels)
  }

  case class Spec(replicas: Option[Int] = Some(1),
                   selector: Option[LabelSelector] = None,
                   template: Option[Pod.Template.Spec] = None)

  case class Status(replicas: Int,
                     fullyLabeledReplicas: Option[Int],
                     observerdGeneration: Option[Int])
}