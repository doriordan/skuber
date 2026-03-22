package skuber.zio

import zio.test.*
import skuber.api.client.Status

object K8sExceptionSpec extends ZIOSpecDefault:
  def spec = suite("K8sException")(
    test("message uses status message when present") {
      val ex = K8sException(Status(message = Some("not found"), code = Some(404)))
      assertTrue(ex.getMessage == "not found")
    },
    test("message falls back to HTTP code when no message") {
      val ex = K8sException(Status(code = Some(500)))
      assertTrue(ex.getMessage.contains("500"))
    },
    test("isNotFound is true for 404") {
      val ex = K8sException(Status(code = Some(404)))
      assertTrue(ex.isNotFound)
    },
    test("isNotFound is false for 403") {
      val ex = K8sException(Status(code = Some(403)))
      assertTrue(!ex.isNotFound)
    },
    test("isConflict is true for 409") {
      val ex = K8sException(Status(code = Some(409)))
      assertTrue(ex.isConflict)
    },
    test("isUnauthorized is true for 401") {
      val ex = K8sException(Status(code = Some(401)))
      assertTrue(ex.isUnauthorized)
    }
  )
