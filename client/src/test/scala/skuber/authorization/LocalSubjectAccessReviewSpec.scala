package skuber.authorization

import org.specs2.mutable.Specification
import play.api.libs.json.Json

import skuber.json.authorization.format._

class LocalSubjectAccessReviewSpec extends Specification {

  "LocalSubjectAccessReview can be round tripped" >> {
    val sar = LocalSubjectAccessReview(
      spec = SubjectAccessReviewSpec(
        user = Some("user"),
        groups = List("group1", "group2"),
        resourceAttributes = Some(ResourceAttributes(
          group = "apps",
          version = "v1",
          resource = "Deployment",
          verb = "create"
        ))
      ),
      status = Some(SubjectAccessReviewStatus(
        allowed = false,
        denied = Some(true),
        reason = Some("User doesn't have access to that resource")
      ))
    )
    Json.toJson(sar).as[LocalSubjectAccessReview] mustEqual sar
  }

}
