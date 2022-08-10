package skuber


/**
 * @author David O'Riordan
 */
case class ReplicationController(val kind: String = "ReplicationController",
                                 override val apiVersion: String = v1,
                                 val metadata: ObjectMeta = ObjectMeta(),
                                 spec: Option[ReplicationController.Spec] = None,
                                 status: Option[ReplicationController.Status] = None)
  extends ObjectResource {

  lazy val copySpec: ReplicationController.Spec = this.spec.getOrElse(new ReplicationController.Spec)

  def withResourceVersion(version: String): ReplicationController = this.copy(metadata = metadata.copy(resourceVersion = version))

  def addLabel(label: Tuple2[String, String]): ReplicationController = this.copy(metadata = metadata.copy(labels = metadata.labels + label))

  def addLabels(newLabels: Map[String, String]): ReplicationController = this.copy(metadata = metadata.copy(labels = metadata.labels ++ newLabels))

  def addAnnotation(anno: Tuple2[String, String]): ReplicationController = this.copy(metadata = metadata.copy(annotations = metadata.annotations + anno))

  def addAnnotations(annos: Map[String, String]): ReplicationController = this.copy(metadata = metadata.copy(annotations = metadata.annotations ++ annos))

  def withReplicas(n: Int): ReplicationController = this.copy(spec = Some(copySpec.copy(replicas = n)))


  def withSelector(s: Map[String, String]): ReplicationController = this.copy(spec = Some(copySpec.copy(selector = Some(s))))

  def withSelector(s: Tuple2[String, String]): ReplicationController = withSelector(Map(s))

  def withTemplate(t: Pod.Template.Spec): ReplicationController = this.copy(spec = Some(copySpec.copy(template = Some(t))))

  /*
   * Set the template from a given Pod spec
   * This automatically creates the template spec metadata - should only be called after the selector has
   * been set on the controller so that the template labels are set to the selector as required by K8S
   */
  def withPodSpec(t: Pod.Spec): ReplicationController = {
    val tmplLabels = spec.flatMap(_.selector).getOrElse(Map[String, String]())
    val template = new Pod.Template.Spec(metadata = ObjectMeta(labels = tmplLabels), spec = Some(t))
    withTemplate(template)
  }
}

object ReplicationController {

  val specification: CoreResourceSpecification = CoreResourceSpecification(scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(plural = "replicationcontrollers",
      singular = "replicationcontroller",
      kind = "ReplicationController",
      shortNames = List("rc")))
  implicit val rcDef: ResourceDefinition[ReplicationController] = new ResourceDefinition[ReplicationController] {
    def spec: ResourceSpecification = specification
  }
  implicit val rcListDef: ResourceDefinition[ReplicationControllerList] = new ResourceDefinition[ReplicationControllerList] {
    def spec: ResourceSpecification = specification
  }

  implicit val scSpec: Scale.SubresourceSpec[ReplicationController] = new Scale.SubresourceSpec[ReplicationController] {
    override def apiVersion = "autoscaling/v1"
  }

  def apply(name: String): ReplicationController = ReplicationController(metadata = ObjectMeta(name = name))

  def apply(name: String, spec: ReplicationController.Spec): ReplicationController =
    ReplicationController(metadata = ObjectMeta(name = name), spec = Some(spec))

  def apply(name: String, container: Container, selector: Map[String, String]): ReplicationController = {
    val podSpec = Pod.Spec(containers = List(container))
    apply(name, podSpec, selector)
  }

  def apply(name: String, podSpec: Pod.Spec, selector: Map[String, String]): ReplicationController = {
    val meta = ObjectMeta(name = name, labels = selector)
    val templSpec = Pod.Template.Spec(metadata = meta, spec = Some(podSpec))
    ReplicationController(metadata = meta, spec = Some(Spec(template = Some(templSpec), selector = Some(selector))))
  }

  case class Spec(replicas: Int = 1,
                   selector: Option[Map[String, String]] = None,
                   template: Option[Pod.Template.Spec] = None)

  case class Status(replicas: Int,
                     observerdGeneration: Option[Int])
}