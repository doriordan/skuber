package skuber

import play.api.libs.json.JsObject
import skuber.api.client.Status

/**
  * @author David O'Riordan
  *
  *  Maintain API-relevant details for a resource type in Kubernetes
  *  Each resource type O (e.g. Pod) implemented in skuber defines an implicit value of type
  *  ResourceDefinition[O] in its companion object which has a method to return a ResourceSpecification.
  *  When a client invokes a skuber API method on a resource of type O then that value gets implicitly
  *  passed to the method, which provides skuber with detail required for the remote call.
  *
  *  ResourceSpecification mirrors the "spec" part of the CustomResourceDefinition type
  *  (see https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definitions)
  *  It is an abstract base class with two concrete case subclasses, for core and non-core API group
  *  resource types respectively.
  */

abstract class ResourceSpecification {
  def apiPathPrefix: String
  def group: Option[String] // set to None if defined on core API group, otherwise Some(groupName)
  def defaultVersion: String // the default version to be used in client requests on resources of this type
  def prioritisedVersions: List[String] // all versions enabled for the resource, sorted by descending priority
  def scope: ResourceSpecification.Scope.Value
  def names: ResourceSpecification.Names
  def versions: Option[List[ResourceSpecification.Version]]
  def subresources: Option[ResourceSpecification.Subresources]
}

object ResourceSpecification {

  object Scope extends Enumeration {
    type ResourceScope = Value
    val Namespaced, Cluster = Value
  }

  final case class Names(
    plural: String,
    singular: String,
    kind: String,
    shortNames: List[String],
    listKind: Option[String] = None,
    categories: List[String] = Nil
  )

  final case class Schema(openAPIV3Schema: JsObject) // the JSON representation of the schema

  final case class Version(
    name: String,
    served: Boolean,
    storage: Boolean,
    schema: Option[Schema],
    subresources: Option[Subresources] = None
  )

  final case class Subresources(status: Option[StatusSubresource] = None, scale: Option[ScaleSubresource] = None) {
    def withStatusSubresource(): Subresources = this.copy(status = Some(StatusSubresource()))
    def withScaleSubresource(scale: ScaleSubresource): Subresources = this.copy(scale = Some(scale))
  }

  final case class ScaleSubresource(specReplicasPath: String, statusReplicasPath: String, labelSelectorPath: Option[String] = None)

  final case class StatusSubresource()
}

final case class CoreResourceSpecification(
  override val group: Option[String] = None,
  version: String = "v1",
  override val scope: ResourceSpecification.Scope.Value,
  override val names: ResourceSpecification.Names,
  override val subresources: Option[ResourceSpecification.Subresources] = None
) extends ResourceSpecification {

  override val apiPathPrefix = "api"
  override val defaultVersion = version
  override val prioritisedVersions: List[String] = List(version)
  override val versions: Option[List[ResourceSpecification.Version]] = None
}

/** NonCoreResourceSpecification is used to specify any resource types outside the core k8s API group, including custom resources
 */
final case class NonCoreResourceSpecification(
  val apiGroup: String,
  val version: Option[String],
  val versions: Option[List[ResourceSpecification.Version]],
  override val scope: ResourceSpecification.Scope.Value,
  override val names: ResourceSpecification.Names,
  override val subresources: Option[ResourceSpecification.Subresources] = None
) extends ResourceSpecification {
  def apiPathPrefix = "apis"
  override def group: Option[String] = Some(apiGroup)

  override lazy val defaultVersion =
    prioritisedVersions.headOption.getOrElse(throw new K8SException(Status(message = Some("No version defined for this resource type"))))

  override lazy val prioritisedVersions: List[String] =
  // return list of served versions sorted by Kubernetes version priority - see
  // https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definition-versioning/

    versions match {
      case None | Some(Nil)  => version.toList
      case Some(versionList) =>
        // return list of served versions sorted by Kubernetes version priority - see
        // https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definition-versioning/

        // Note: this pattern matches Kubernetes versions - matching will return nulls for the optional second and third groups if a GA version e.g. "v1"
        val kubernetesVersionPattern = """"v(\\d+)(alpha|beta)?(\\d+)?""".r

        def nullToGA(str: String) = if (str == null) "GA" else str
        val kubernetesReleaseStages = List[String]("GA", "beta", "alpha") // ordered from highest to lowest priority, null means GA
        def byReleaseStagePriority(stage1: String, stage2: String): Boolean =
          kubernetesReleaseStages.indexOf(stage1) > kubernetesReleaseStages.indexOf(stage2)

        def byVersionPriority(version1: String, version2: String): Boolean = {
          val versionOneMatch = kubernetesVersionPattern.findFirstMatchIn(version1)
          val versionTwoMatch = kubernetesVersionPattern.findFirstMatchIn(version2)
          (versionOneMatch, versionTwoMatch) match {
            case (None, Some(_)) => false
            case (Some(_), None) => true
            case (None, None)    => version1 < version2
            case (Some(v1match), Some(v2match)) =>
              if (v1match.group(0) != v2match.group(0))
                v1match.group(0) > v2match.group(0)
              else if (v1match.group(1) != v2match.group(1))
                byReleaseStagePriority(nullToGA(v1match.group(1)), nullToGA(v2match.group(1)))
              else {
                if (v1match.group(2) == null)
                  true
                else if (v2match.group(2) == null)
                  false
                else
                  v1match.group(2) > v2match.group(2)
              }
          }
        }

        versionList
          .filter(_.served)
          .map(_.name)
          .sortWith(byVersionPriority)
    }
}

object NonCoreResourceSpecification {

  def apply(apiGroup: String, version: String, scope: ResourceSpecification.Scope.Value, names: ResourceSpecification.Names): NonCoreResourceSpecification = {
    val versions = List(ResourceSpecification.Version(name = version, served = true, storage = true, schema = None))
    new NonCoreResourceSpecification(apiGroup, Some(version), Some(versions), scope, names, None)
  }
}
