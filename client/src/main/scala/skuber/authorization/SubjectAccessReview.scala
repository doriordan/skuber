package skuber.authorization

import skuber.ResourceSpecification.{ Names, Scope }
import skuber.rbac.rbacAPIVersion
import skuber.{ NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition }

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
  implicit val subjectAccessReviewDef = new ResourceDefinition[SubjectAccessReview] {
    def spec = NonCoreResourceSpecification (
      apiGroup="authorization.k8s.io",
      version="v1",
      scope = Scope.Cluster,
      names=Names(
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
  implicit val localSubjectAccessReviewDef = new ResourceDefinition[LocalSubjectAccessReview] {
    def spec = NonCoreResourceSpecification (
      apiGroup="authorization.k8s.io",
      version="v1",
      scope = Scope.Namespaced,
      names=Names(
        plural = "localsubjectaccessreviews",
        singular = "localsubjectaccessreview",
        kind = "LocalSubjectAccessReview",
        shortNames = Nil
      )
    )
  }
}
