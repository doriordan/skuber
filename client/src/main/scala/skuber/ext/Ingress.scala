package skuber.ext

/**
  * @author David O'Riordan
  */

import skuber.ResourceSpecification.{Names, Scope}
import skuber._
import skuber.ext.Ingress.Backend

case class Ingress(
  val kind: String ="Ingress",
  override val apiVersion: String = extensionsAPIVersion,
  val metadata: ObjectMeta = ObjectMeta(),
  spec: Option[Ingress.Spec] = None,
  status: Option[Ingress.Status] = None)
  extends ObjectResource {

  lazy val copySpec: Ingress.Spec = this.spec.getOrElse(new Ingress.Spec)

  /*
   * Fluent API method for building out ingress rules e.g.
   * val ingress = Ingress("microservices").
   *   addHttpRule("foo.bar.com",
   *                Map("/order" -> "orderService:80", "inventory" -> "inventoryService:80").
   *   addHttpRule("foo1.bar.com",
   *                Map("/ship" -> "orderService:80", "inventory" -> "inventoryService:80").
   *
   */
  def addHttpRule(host:String, pathsMap: Map[String, String]): Ingress = {
    val paths: List[Ingress.Path] = pathsMap map { case (path: String, backend: String) =>
       val beParts = backend.split(':')
       if (beParts.size != 2)
         throw new Exception("invalid backend format: expected \"serviceName:servicePort\"")
       val serviceName=beParts(0)
       val servicePort=beParts(1).toInt
       Ingress.Path(path,Ingress.Backend(serviceName, servicePort))
    } toList
    val httpRule = Ingress.HttpRule(paths)
    val rule = Ingress.Rule(host, httpRule)
    val baseSpec=copySpec
    val withRuleSpec=copySpec.copy(rules = copySpec.rules :+ rule)
    this.copy(spec = Some(withRuleSpec))
  }

  // set the default backend i.e. if no ingress rule matches the incoming traffic then it gets routed to the specified service
  def withDefaultBackendService(serviceName: String, servicePort: Int = 0): Ingress = {
    val be = Backend(serviceName,servicePort)
    this.copy(spec=Some(copySpec.copy(backend = Some(be)))
    )
  }
}

object Ingress {

  val specification=NonCoreResourceSpecification(
    group = Some("extensions"),
    version = "v1beta1",
    scope = Scope.Namespaced,
    names = Names(
      plural = "ingresses",
      singular = "ingress",
      kind = "Ingress",
      shortNames = List("ing")
    )
  )
  implicit val ingDef = new ResourceDefinition[Ingress] { def spec=specification }
  implicit val ingListDef = new ResourceDefinition[IngressList] { def spec=specification }

  def apply(name: String) : Ingress = Ingress(metadata=ObjectMeta(name=name))

  case class Backend(serviceName: String, servicePort: Int = 0)
  case class Path(path: String, backend: Backend)
  case class HttpRule(paths: List[Path] = List())
  case class Rule(host: String, http: HttpRule)
  case class TLS(hosts: List[String]=List(), secretName: String="")

  case class Spec(
    backend: Option[Backend] = None,
    rules: List[Rule] = List(),
    tls: List[TLS]=List())

  case class Status(loadBalancer: Option[Status.LoadBalancer] = None)

  object Status {
    case class LoadBalancer(ingress: List[LoadBalancer.Ingress])
    object LoadBalancer {
      case class Ingress(ip: Option[String] = None, hostName: Option[String] = None)
    }
  }
}