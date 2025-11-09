package skuber.json.authorization

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.authorization._
import skuber.json.format._ // reuse some core formatters

package object format {

  implicit val nonResourceAttributesFormat: Format[NonResourceAttributes] = (
    (JsPath \ "path").format[String] and
      (JsPath \ "verb").format[String]
    ) (NonResourceAttributes.apply, unlift(NonResourceAttributes.unapply))

  implicit val resourceAttributesFormat: Format[ResourceAttributes] = (
    (JsPath \ "group").format[String] and
      (JsPath \ "version").format[String] and
      (JsPath \ "resource").format[String] and
      (JsPath \ "verb").format[String] and
      (JsPath \ "subresource").formatNullable[String] and
      (JsPath \ "namespace").formatNullable[String] and
      (JsPath \ "name").formatNullable[String]
    ) (ResourceAttributes.apply, unlift(ResourceAttributes.unapply))

  implicit val subjectAccessReviewSpecFormat: Format[SubjectAccessReviewSpec] = (
    (JsPath \ "extra").formatMaybeEmptyMap[String] and
    (JsPath \ "groups").formatMaybeEmptyList[String] and
    (JsPath \ "nonResourceAttributes").formatNullable[NonResourceAttributes] and
    (JsPath \ "resourceAttributes").formatNullable[ResourceAttributes] and
    (JsPath \ "uid").formatNullable[String] and
    (JsPath \ "user").formatNullable[String]
  )(SubjectAccessReviewSpec.apply, unlift(SubjectAccessReviewSpec.unapply))

  implicit val subjectAccessReviewStatusFormat: Format[SubjectAccessReviewStatus] = (
    (JsPath \ "allowed").format[Boolean] and
    (JsPath \ "denied").formatNullable[Boolean] and
    (JsPath \ "evaluationError").formatNullable[String] and
    (JsPath \ "reason").formatNullable[String]
  )(SubjectAccessReviewStatus.apply, unlift(SubjectAccessReviewStatus.unapply))

  implicit val subjectAccessReviewFormat: Format[SubjectAccessReview] = (
    objFormat and
    (JsPath \ "spec").format[SubjectAccessReviewSpec] and
    (JsPath \ "status").formatNullable[SubjectAccessReviewStatus]
  )(SubjectAccessReview.apply, unlift(SubjectAccessReview.unapply))

  implicit val localSubjectAccessReviewFormat: Format[LocalSubjectAccessReview] = (
    objFormat and
    (JsPath \ "spec").format[SubjectAccessReviewSpec] and
    (JsPath \ "status").formatNullable[SubjectAccessReviewStatus]
  )(LocalSubjectAccessReview.apply, unlift(LocalSubjectAccessReview.unapply))

}
