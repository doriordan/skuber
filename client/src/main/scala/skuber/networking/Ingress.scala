package skuber.networking

/**
  * @author David O'Riordan
  */

import skuber.ResourceSpecification.{Names, Scope}
import skuber._
import scala.util.Try

case class Ingress(kind: String ="Ingress",
  override val apiVersion: String = "networking.k8s.io/v1beta1",
  metadata: ObjectMeta = ObjectMeta(),
  spec: Option[Ingress.Spec] = None,
  status: Option[Ingress.Status] = None)
  extends ObjectResource {

  import skuber.networking.Ingress.Backend

  lazy val copySpec: Ingress.Spec = this.spec.getOrElse(new Ingress.Spec)

  /**
   * Fluent API method for building out ingress rules e.g.
   * {{{
   * val ingress = Ingress("microservices").
   *   addHttpRule("foo.bar.com",
   *                Map("/order" -> "orderService:80",
   *                    "inventory" -> "inventoryService:80")).
   *   addHttpRule("foo1.bar.com",
   *                Map("/ship" -> "orderService:80",
   *                    "inventory" -> "inventoryService:80")).
   * }}}
   */
  def addHttpRule(host: String, pathsMap: Map[String, String]): Ingress =
    addHttpRule(Some(host), pathsMap)

  /**
   * Fluent API method for building out ingress rules without host e.g.
   * {{{
   * val ingress = Ingress("microservices").
   *   addHttpRule(Map("/order" -> "orderService:80",
   *                    "inventory" -> "inventoryService:80")).
   *   addHttpRule(Map("/ship" -> "orderService:80",
   *                    "inventory" -> "inventoryService:80")).
   * }}}
   */
  def addHttpRule(pathsMap: Map[String, String]): Ingress =
    addHttpRule(Option.empty, pathsMap)

  /**
   * Fluent API method for building out ingress rules e.g.
   * {{{
   * val ingress =
   *   Ingress("microservices")
   *     .addHttpRule("foo.bar.com",
   *                  "/order" -> "orderService:80",
   *                  "inventory" -> "inventoryService:80")
   *     .addHttpRule("foo1.bar.com",
   *                  "/ship" -> "orderService:80",
   *                  "inventory" -> "inventoryService:80").
   * }}}
   */
  def addHttpRule(host: String, pathsMap: (String, String)*): Ingress =
    addHttpRule(Some(host), pathsMap.toMap)

  /**
   * Fluent API method for building out ingress rules without host e.g.
   * {{{
   * val ingress =
   *   Ingress("microservices")
   *     .addHttpRule("/order" -> "orderService:80",
   *                  "inventory" -> "inventoryService:80")
   *     .addHttpRule("/ship" -> "orderService:80",
   *                  "inventory" -> "inventoryService:80").
   * }}}
   */
  def addHttpRule(pathsMap: (String, String)*): Ingress =
    addHttpRule(Option.empty, pathsMap.toMap)

  private val backendSpec = "(\\S+):(\\S+)".r

  /**
   * Fluent API method for building out ingress rules e.g.
   * {{{
   * val ingress =
   *   Ingress("microservices")
   *     .addHttpRule(Some("foo.bar.com"),
   *                  Map("/order" -> "orderService:80",
   *                      "inventory" -> "inventoryService:80"))
   *     .addHttpRule(None,
   *                  Map("/ship" -> "orderService:80",
   *                      "inventory" -> "inventoryService:80")).
   * }}}
   */
  def addHttpRule(host: Option[String], pathsMap: Map[String, String]): Ingress = {
    val paths: List[Ingress.Path] = pathsMap.map { case (path: String, backend: String) =>
      backend match {
        case backendSpec(serviceName, servicePort) =>
          Ingress.Path(path,Ingress.Backend(serviceName, toNameablePort(servicePort)))
        case _ => throw new Exception(s"invalid backend format: expected 'serviceName:servicePort' (got '$backend', for host: $host)")
      }

    }.toList
    val httpRule = Ingress.HttpRule(paths)
    val rule = Ingress.Rule(host, httpRule)
    val withRuleSpec=copySpec.copy(rules = copySpec.rules :+ rule)
    this.copy(spec = Some(withRuleSpec))
  }

  /**
   * set the default backend i.e. if no ingress rule matches the incoming traffic then it gets routed to the specified service
   * @param serviceNameAndPort - service name and port as 'serviceName:servicePort'
   * @return copy of this Ingress with default backend set
   */
  def withDefaultBackendService(serviceNameAndPort: String): Ingress = {
    serviceNameAndPort match {
      case backendSpec(serviceName, servicePort) =>
        withDefaultBackendService(serviceName, toNameablePort(servicePort))
      case _ => throw new Exception(s"invalid default backend format: expected 'serviceName:servicePort' (got '$serviceNameAndPort')")
    }
  }

  /**
   * set the default backend i.e. if no ingress rule matches the incoming traffic then it gets routed to the specified service
   * @param serviceName - service name
   * @param servicePort - service port
   * @return copy of this Ingress with default backend set
   */
  def withDefaultBackendService(serviceName: String, servicePort: NameablePort): Ingress = {
    val be = Backend(serviceName, servicePort)
    this.copy(spec=Some(copySpec.copy(backend = Some(be))))
  }

  def addAnnotations(newAnnos: Map[String, String]): Ingress =
    this.copy(metadata = this.metadata.copy(annotations = this.metadata.annotations ++ newAnnos))

  private def toNameablePort(port: String): NameablePort =
    Try(port.toInt).toEither.left.map(_ => port).swap
}

object Ingress {

  val specification: NonCoreResourceSpecification = NonCoreResourceSpecification(apiGroup = "networking.k8s.io",
    version = "v1beta1",
    scope = Scope.Namespaced,
    names = Names(plural = "ingresses",
      singular = "ingress",
      kind = "Ingress",
      shortNames = List("ing")))
  implicit val ingDef: ResourceDefinition[Ingress] = new ResourceDefinition[Ingress] { def spec=specification }
  implicit val ingListDef: ResourceDefinition[IngressList] = new ResourceDefinition[IngressList] { def spec=specification }

  def apply(name: String) : Ingress = Ingress(metadata=ObjectMeta(name=name))

  case class Backend(serviceName: String, servicePort: NameablePort)
  case class Path(path: String, backend: Backend)
  case class HttpRule(paths: List[Path] = List())
  case class Rule(host: Option[String], http: HttpRule)
  case class TLS(hosts: List[String]=List(), secretName: Option[String] = None)

  case class Spec(backend: Option[Backend] = None,
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
