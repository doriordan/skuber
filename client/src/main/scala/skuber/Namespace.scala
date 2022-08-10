package skuber


/**
 * @author David O'Riordan
 */
case class Namespace(val kind: String = "Namespace",
                     override val apiVersion: String = v1,
                     val metadata: ObjectMeta,
                     spec: Option[Namespace.Spec] = None,
                     status: Option[Namespace.Status] = None)
  extends ObjectResource {
  def meta(name: String): ObjectMeta = ObjectMeta(name = name, namespace = this.name)

  def pod(name: String): Pod = Pod(metadata = meta(name))

  def pod(name: String, spec: Pod.Spec): Pod = Pod(metadata = meta(name), spec = Some(spec))

  def node(name: String): Node = Node(metadata = meta(name))

  def node(name: String, spec: Node.Spec): Node = Node(metadata = meta(name), spec = Some(spec))

  def replicationController(name: String): ReplicationController = ReplicationController(metadata = meta(name))

  def replicationController(name: String, spec: ReplicationController.Spec): ReplicationController =
    ReplicationController(metadata = meta(name), spec = Some(spec))

  def service(name: String): Service = Service(metadata = meta(name))

  def service(name: String, spec: Service.Spec): Service = Service(metadata = meta(name), spec = Some(spec))

  def withFinalizers(f: List[String]): Namespace = {
    this.copy(spec = Some(Namespace.Spec(Option(f))))
  }

  def withStatusOfPhase(p: String): Namespace = {
    this.copy(status = Some(Namespace.Status(p)))
  }

  def withResourceVersion(version: String): Namespace = this.copy(metadata = metadata.copy(resourceVersion = version))
}

object Namespace {

  val specification: CoreResourceSpecification = CoreResourceSpecification(scope = ResourceSpecification.Scope.Cluster,
    names = ResourceSpecification.Names(plural = "namespaces",
      singular = "namespace",
      kind = "Namespace",
      shortNames = List("ns")))
  implicit val namespaceDef: ResourceDefinition[Namespace] = new ResourceDefinition[Namespace] {
    def spec: ResourceSpecification = specification
  }
  implicit val nsListDef: ResourceDefinition[NamespaceList] = new ResourceDefinition[NamespaceList] {
    def spec: ResourceSpecification = specification
  }

  case class Spec(finalizers: Option[List[String]])

  case class Status(phase: String)

  lazy val default: Namespace = Namespace.forName("default")
  lazy val system: Namespace = Namespace.forName("kube-system")
  lazy val all: Namespace = Namespace.forName("")
  lazy val none: Namespace = all

  def forName(label: String): Namespace = Namespace(metadata = ObjectMeta(name = label))

  def from(meta: ObjectMeta): Namespace = Namespace(metadata = meta)

  def apply(label: String): Namespace = Namespace(metadata = ObjectMeta(name = label))
}