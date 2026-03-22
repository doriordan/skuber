package skuber.zio

import zio.*
import zio.stream.*
import zio.test.*
import skuber.zio.internal.PlayJsonBridge
import play.api.libs.json.{Json, Reads}

object PlayJsonBridgeSpec extends ZIOSpecDefault:
  case class Foo(x: Int)
  given Reads[Foo] = Json.reads[Foo]

  def spec = suite("PlayJsonBridge.parseJsonLines")(
    test("parses valid newline-delimited JSON") {
      val ndjson = "{\"x\":1}\n{\"x\":2}\n"
      val bytes  = ZStream.fromIterable(ndjson.getBytes("UTF-8"))
      PlayJsonBridge.parseJsonLines[Foo](bytes).runCollect.map: results =>
        assertTrue(results.size == 2)
        assertTrue(results(0) == Right(Foo(1)))
        assertTrue(results(1) == Right(Foo(2)))
    },
    test("returns Left for malformed line, continues stream") {
      val ndjson = "{\"x\":1}\n{bad json}\n{\"x\":3}\n"
      val bytes  = ZStream.fromIterable(ndjson.getBytes("UTF-8"))
      PlayJsonBridge.parseJsonLines[Foo](bytes).runCollect.map: results =>
        assertTrue(results.size == 3)
        assertTrue(results(0).isRight)
        assertTrue(results(1).isLeft)
        assertTrue(results(2).isRight)
    },
    test("ignores blank lines") {
      val ndjson = "{\"x\":1}\n\n{\"x\":2}\n"
      val bytes  = ZStream.fromIterable(ndjson.getBytes("UTF-8"))
      PlayJsonBridge.parseJsonLines[Foo](bytes).runCollect.map: results =>
        assertTrue(results.size == 2)
    }
  )
