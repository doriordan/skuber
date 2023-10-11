package skuber.model.apps.v1

import play.api.libs.json.OFormat
import skuber.model.ResourceSpecification.{Names, Scope}
import skuber.model._

/**
  * @author David O'Riordan
  */
case class ReplicaSet(
  kind: String ="ReplicaSet",
  apiVersion: String = appsAPIVersion,
  metadata: ObjectMeta = ObjectMeta(),
  spec: Option[ReplicaSet.Spec] = None,
  status: Option[ReplicaSet.Status] = None)
    extends ObjectResource {

  lazy val copySpec = this.spec.getOrElse(ReplicaSet.Spec(selector=LabelSelector(), template=Pod.Template.Spec()))

  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))
  def addLabel(label: Tuple2[String, String]) : ReplicaSet = this.copy(metadata = metadata.copy(labels = metadata.labels + label))
  def addLabels(newLabels: Map[String, String]) : ReplicaSet = this.copy(metadata = metadata.copy(labels = metadata.labels ++ newLabels))
  def addAnnotation(anno: Tuple2[String, String]) : ReplicaSet = this.copy(metadata = metadata.copy(annotations = metadata.annotations + anno))
  def addAnnotations(annos: Map[String, String]) : ReplicaSet = this.copy(metadata = metadata.copy(annotations = metadata.annotations ++ annos))

  def withReplicas(n: Int) = this.copy(spec=Some(copySpec.copy(replicas=Some(n))))


  def withSelector(s: LabelSelector) : ReplicaSet = this.copy(spec=Some(copySpec.copy(selector = s)))
  def withSelector(s: Tuple2[String,String]) : ReplicaSet = withSelector(LabelSelector(LabelSelector.IsEqualRequirement(s._1,s._2)))

  /*
   * Set the template. This will set the selector from the template labels, if they exist
   * and the selector is empty
   */
  def withTemplate(t: Pod.Template.Spec) = {
    val withTmpl = this.copy(spec = Some(copySpec.copy(template = t)))
    val withSelector = (t.metadata.labels, spec.map{_.selector}) match {
      case (labels, selector) if labels.nonEmpty && !selector.exists(_.requirements.nonEmpty) =>
        val reqs = labels.map { l =>
          LabelSelector.IsEqualRequirement(l._1, l._2)
        }
        val selector = LabelSelector(reqs.toSeq: _*)
        withTmpl.withSelector(selector)
      case _ => withTmpl
    }
    // copy template labels into RS labels, if not already set
    (metadata.labels,t.metadata.labels) match {
      case (curr,default) if curr.isEmpty && default.nonEmpty =>
        withSelector.addLabels(default)
      case _ => withSelector
    }
  }

  /*
   * Set the template from a given Pod spec and optional set of labels
   * If the selector isn't already set then this will generate it from the labels.
   */
  def withPodSpec(t: Pod.Spec, labels: Map[String, String]=Map()) = {
    val template = new Pod.Template.Spec(metadata=ObjectMeta(labels=labels),spec=Some(t))
    withTemplate(template)
  }
}

object ReplicaSet {

  val specification=NonCoreResourceSpecification(
    apiGroup = "apps",
    version = "v1",
    scope = Scope.Namespaced,
    names = Names(
      plural = "replicasets",
      singular = "replicaset",
      kind = "ReplicaSet",
      shortNames = List("rs")
    )
  )
  implicit val rsDef: ResourceDefinition[ReplicaSet] = new ResourceDefinition[ReplicaSet] { def spec=specification }
  implicit val rsListDef: ResourceDefinition[ReplicaSetList] = new ResourceDefinition[ReplicaSetList] { def spec=specification }
  implicit val scDef: Scale.SubresourceSpec[ReplicaSet] = new Scale.SubresourceSpec[ReplicaSet] { override def apiVersion: String = "extensions/v1beta1"}

  def apply(name: String) : ReplicaSet = ReplicaSet(metadata=ObjectMeta(name=name))
  def apply(name: String, spec: ReplicaSet.Spec) : ReplicaSet =
    ReplicaSet(metadata=ObjectMeta(name=name), spec = Some(spec))
  def apply(name:String, container: Container) : ReplicaSet = {
    val podSpec=Pod.Spec(containers=List(container))
    ReplicaSet(name, podSpec, Map[String,String]())
  }
  def apply(
    name:String,
    podSpec: Pod.Spec,
    labels: Map[String,String]) : ReplicaSet =
  {
    val meta=ObjectMeta(name=name, labels = labels)
    ReplicaSet(metadata=meta).withPodSpec(podSpec, labels)
  }

  case class Spec(
    replicas: Option[Int]=Some(1),
    minReadySeconds: Option[Int] = None,
    selector: LabelSelector,
    template: Pod.Template.Spec)

  case class Status(
    replicas: Int,
    fullyLabeledReplicas: Option[Int],
    observerdGeneration: Option[Int])

  // json formatters

  import play.api.libs.json.{Json, Format, JsPath}
  import play.api.libs.functional.syntax._
  import skuber.json.format._

  implicit val replsetSpecFormat: Format[ReplicaSet.Spec] = (
    (JsPath \ "replicas").formatNullable[Int] and
    (JsPath \ "minReadySeconds").formatNullable[Int]   and
    (JsPath \ "selector").formatLabelSelector and
    (JsPath \ "template").format[Pod.Template.Spec]
  )(ReplicaSet.Spec.apply _, r => (r.replicas, r.minReadySeconds, r.selector, r.template))

  implicit val replsetStatusFormat: Format[Status] = Json.format[ReplicaSet.Status]

  implicit lazy val replsetFormat: Format[ReplicaSet] = (
    objFormat and
    (JsPath \ "spec").formatNullable[ReplicaSet.Spec] and
    (JsPath \ "status").formatNullable[ReplicaSet.Status]
  )(ReplicaSet.apply _, r => (r.kind, r.apiVersion, r.metadata, r.spec, r.status))
}
