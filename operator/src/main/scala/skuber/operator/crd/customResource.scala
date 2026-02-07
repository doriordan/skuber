package skuber.operator.crd

import scala.annotation.{MacroAnnotation, experimental}
import scala.quoted.*

enum Scope:
  case Namespaced, Cluster

/**
 * Annotation macro for defining Kubernetes custom resources with minimal boilerplate.
 *
 * Usage:
 * {{{
 * @customResource(
 *   group = "mycompany.com",
 *   version = "v1",
 *   kind = "WebApp"
 * )
 * object WebApp:
 *   case class Spec(replicas: Int, image: String, port: Int = 8080)
 *   case class Status(availableReplicas: Int, ready: Boolean)
 * }}}
 *
 * This generates:
 * - `type WebApp = CustomResource[Spec, Status]`
 * - `given Format[Spec]` and `given Format[Status]`
 * - `given ResourceDefinition[WebApp]`
 * - `given HasStatusSubresource[WebApp]` (if Status class exists)
 * - `given Scale.SubresourceSpec[WebApp]` (if Spec has `replicas: Int`)
 * - `def crd: CustomResourceDefinition`
 * - `def apply(spec: Spec): WebApp`
 *
 * @param group API group (e.g., "mycompany.com")
 * @param version API version (e.g., "v1", "v1alpha1")
 * @param kind Resource kind (e.g., "WebApp")
 * @param plural Plural name (defaults to lowercase kind + "s")
 * @param singular Singular name (defaults to lowercase kind)
 * @param shortNames Comma-separated kubectl short names (e.g., "q,qu")
 * @param scope "Namespaced" or "Cluster"
 * @param statusSubresource "auto" (detect from Status class), "true", or "false"
 * @param scaleSubresource "auto" (detect from replicas field), "true", or "false"
 */
@experimental
class customResource(
  group: String,
  version: String,
  kind: String,
  plural: String = "",
  singular: String = "",
  shortNames: String = "",
  scope: Scope = Scope.Namespaced,
  statusSubresource: String = "auto",
  scaleSubresource: String = "auto"
) extends MacroAnnotation:
  def transform(using Quotes)(
    tree: quotes.reflect.Definition,
    companion: Option[quotes.reflect.Definition]
  ): List[quotes.reflect.Definition] =
    val shortNamesList = if shortNames.isEmpty then Nil else shortNames.split(",").map(_.trim).toList
    val statusOpt = statusSubresource match
      case "auto" => None
      case "true" => Some(true)
      case "false" => Some(false)
      case other => throw new IllegalArgumentException(s"statusSubresource must be 'auto', 'true', or 'false', got: '$other'")
    val scaleOpt = scaleSubresource match
      case "auto" => None
      case "true" => Some(true)
      case "false" => Some(false)
      case other => throw new IllegalArgumentException(s"scaleSubresource must be 'auto', 'true', or 'false', got: '$other'")
    CustomResourceMacro.transform(
      tree, group, version, kind,
      plural, singular, shortNamesList, scope,
      statusOpt, scaleOpt
    )
