package skuber.model.authorization

import play.api.libs.functional.syntax._
import play.api.libs.json._
import skuber.model.ResourceSpecification.{Names, Scope}
import skuber.json.format.{objFormat, maybeEmptyFormatMethods}
import skuber.model.{NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition}

case class SubjectAccessReviewSpec(
    extra: Map[String, String] = Map.empty,
    groups: List[String] = Nil,
    nonResourceAttributes: Option[NonResourceAttributes] = None,
    resourceAttributes: Option[ResourceAttributes] = None,
    uid: Option[String] = None,
    user: Option[String] = None
)

case class NonResourceAttributes(path: String, verb: String)

case class ResourceAttributes(
    group: String,
    version: String,
    resource: String,
    verb: String,
    subresource: Option[String] = None,
    namespace: Option[String] = None,
    name: Option[String] = None
)

case class SubjectAccessReviewStatus(
    allowed: Boolean,
    denied: Option[Boolean] = None,
    evaluationError: Option[String] = None,
    reason: Option[String] = None
)

case class SubjectAccessReview(
    kind: String = "SubjectAccessReview",
    apiVersion: String = authorizationAPIVersion,
    metadata: ObjectMeta = ObjectMeta(),
    spec: SubjectAccessReviewSpec,
    status: Option[SubjectAccessReviewStatus] = None,
) extends ObjectResource

object SubjectAccessReview {
  implicit val subjectAccessReviewDef: ResourceDefinition[SubjectAccessReview] = new ResourceDefinition[SubjectAccessReview] {
    def spec = NonCoreResourceSpecification(
      apiGroup = "authorization.k8s.io",
      version = "v1",
      scope = Scope.Cluster,
      names = Names(
        plural = "subjectaccessreviews",
        singular = "subjectaccessreview",
        kind = "SubjectAccessReview",
        shortNames = Nil
      )
    )
  }
}

case class LocalSubjectAccessReview(
  kind: String = "LocalSubjectAccessReview",
  apiVersion: String = authorizationAPIVersion,
  metadata: ObjectMeta = ObjectMeta(),
  spec: SubjectAccessReviewSpec,
  status: Option[SubjectAccessReviewStatus] = None,
) extends ObjectResource

object LocalSubjectAccessReview {
  implicit val localSubjectAccessReviewDef: ResourceDefinition[LocalSubjectAccessReview] = new ResourceDefinition[LocalSubjectAccessReview] {
    def spec = NonCoreResourceSpecification(
      apiGroup = "authorization.k8s.io",
      version = "v1",
      scope = Scope.Namespaced,
      names = Names(
        plural = "localsubjectaccessreviews",
        singular = "localsubjectaccessreview",
        kind = "LocalSubjectAccessReview",
        shortNames = Nil
      )
    )
  }

  implicit val nonResourceAttributesFormat: Format[NonResourceAttributes] = (
    (JsPath \ "path").format[String] and
    (JsPath \ "verb").format[String]
  ) (NonResourceAttributes.apply, n => (n.path, n.verb ))

  implicit val resourceAttributesFormat: Format[ResourceAttributes] = (
    (JsPath \ "group").format[String] and
    (JsPath \ "version").format[String] and
    (JsPath \ "resource").format[String] and
    (JsPath \ "verb").format[String] and
    (JsPath \ "subresource").formatNullable[String] and
    (JsPath \ "namespace").formatNullable[String] and
    (JsPath \ "name").formatNullable[String]
  ) (ResourceAttributes.apply, r => (r.group, r.version, r.resource, r.verb, r.subresource, r.namespace, r.name))

  implicit val subjectAccessReviewSpecFormat: Format[SubjectAccessReviewSpec] = (
    (JsPath \ "extra").formatMaybeEmptyMap[String] and
    (JsPath \ "groups").formatMaybeEmptyList[String] and
    (JsPath \ "nonResourceAttributes").formatNullable[NonResourceAttributes] and
    (JsPath \ "resourceAttributes").formatNullable[ResourceAttributes] and
    (JsPath \ "uid").formatNullable[String] and
    (JsPath \ "user").formatNullable[String]
  )(SubjectAccessReviewSpec.apply, s => (s.extra, s.groups, s.nonResourceAttributes, s.resourceAttributes, s.uid, s.user))

  implicit val subjectAccessReviewStatusFormat: Format[SubjectAccessReviewStatus] = (
    (JsPath \ "allowed").format[Boolean] and
    (JsPath \ "denied").formatNullable[Boolean] and
    (JsPath \ "evaluationError").formatNullable[String] and
    (JsPath \ "reason").formatNullable[String]
  )(SubjectAccessReviewStatus.apply, s => (s.allowed, s.denied, s.evaluationError, s.reason))

  implicit val subjectAccessReviewFormat: Format[SubjectAccessReview] = (
    objFormat and
    (JsPath \ "spec").format[SubjectAccessReviewSpec] and
    (JsPath \ "status").formatNullable[SubjectAccessReviewStatus]
  )(SubjectAccessReview.apply, s => (s.kind, s.apiVersion, s.metadata, s.spec, s.status))

  implicit val localSubjectAccessReviewFormat: Format[LocalSubjectAccessReview] = (
    objFormat and
    (JsPath \ "spec").format[SubjectAccessReviewSpec] and
    (JsPath \ "status").formatNullable[SubjectAccessReviewStatus]
  )(LocalSubjectAccessReview.apply, l => (l.kind, l.apiVersion, l.metadata, l.spec, l.status))
}
