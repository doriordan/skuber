package skuber.zio

import zio.test.*
import skuber.api.client.{K8SException, Status}

object K8SExceptionSpec extends ZIOSpecDefault:
  def spec = suite("K8SException")(
    test("message uses status message when present") {
      val ex = new K8SException(Status(message = Some("not found"), code = Some(404)))
      assertTrue(ex.getMessage == "not found")
    },
    test("message falls back to HTTP code when no message") {
      val ex = new K8SException(Status(code = Some(500)))
      assertTrue(ex.getMessage.contains("500"))
    },
    test("isNotFound is true for 404") {
      val ex = new K8SException(Status(code = Some(404)))
      assertTrue(ex.isNotFound)
    },
    test("isNotFound is false for 403") {
      val ex = new K8SException(Status(code = Some(403)))
      assertTrue(!ex.isNotFound)
    },
    test("isConflict is true for 409") {
      val ex = new K8SException(Status(code = Some(409)))
      assertTrue(ex.isConflict)
    },
    test("isUnauthorized is true for 401") {
      val ex = new K8SException(Status(code = Some(401)))
      assertTrue(ex.isUnauthorized)
    }
  )
