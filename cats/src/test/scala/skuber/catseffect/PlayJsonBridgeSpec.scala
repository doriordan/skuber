package skuber.catseffect

import cats.effect.IO
import fs2.Stream
import munit.CatsEffectSuite
import play.api.libs.json.{Json, Reads, Writes, OFormat}
import skuber.catseffect.internal.PlayJsonBridge

case class TestPerson(name: String, age: Int)

object TestPerson:
  given OFormat[TestPerson] = Json.format[TestPerson]

class PlayJsonBridgeSpec extends CatsEffectSuite:

  test("encode produces valid JSON bytes"):
    val person = TestPerson("Alice", 30)
    val bytes = PlayJsonBridge.encode(person)
    val json = new String(bytes, "UTF-8")
    assert(json.contains("\"name\""))
    assert(json.contains("\"Alice\""))
    assert(json.contains("\"age\""))
    assert(json.contains("30"))

  test("decode successfully parses valid JSON"):
    val json = """{"name":"Bob","age":25}"""
    val result = PlayJsonBridge.decode[TestPerson](json.getBytes("UTF-8"))
    assertEquals(result, Right(TestPerson("Bob", 25)))

  test("decode returns Left for invalid JSON"):
    val json = """{"name":"Bob"}"""
    val result = PlayJsonBridge.decode[TestPerson](json.getBytes("UTF-8"))
    assert(result.isLeft)
    assert(result.left.exists(_.contains("JSON decode error")))

  test("decode returns Left for malformed JSON"):
    val json = """not json at all"""
    val result = PlayJsonBridge.decode[TestPerson](json.getBytes("UTF-8"))
    assert(result.isLeft)

  test("decodeOrThrow returns value for valid JSON"):
    val json = """{"name":"Charlie","age":40}"""
    val person = PlayJsonBridge.decodeOrThrow[TestPerson](json.getBytes("UTF-8"))
    assertEquals(person, TestPerson("Charlie", 40))

  test("decodeOrThrow throws RuntimeException for invalid JSON"):
    val json = """{"name":"Charlie"}"""
    intercept[RuntimeException]:
      PlayJsonBridge.decodeOrThrow[TestPerson](json.getBytes("UTF-8"))

  test("encode then decode roundtrips"):
    val original = TestPerson("Dana", 35)
    val bytes = PlayJsonBridge.encode(original)
    val result = PlayJsonBridge.decode[TestPerson](bytes)
    assertEquals(result, Right(original))

  test("parseJsonLines parses newline-delimited JSON stream"):
    val lines = """{"name":"Alice","age":30}
{"name":"Bob","age":25}
{"name":"Charlie","age":40}"""
    val stream: Stream[IO, Byte] = Stream.emits(lines.getBytes("UTF-8"))
    PlayJsonBridge.parseJsonLines[IO, TestPerson](stream).compile.toList.map: results =>
      assertEquals(results.length, 3)
      assertEquals(results(0), Right(TestPerson("Alice", 30)))
      assertEquals(results(1), Right(TestPerson("Bob", 25)))
      assertEquals(results(2), Right(TestPerson("Charlie", 40)))

  test("parseJsonLines handles invalid lines"):
    val lines = """{"name":"Alice","age":30}
not valid json
{"name":"Bob","age":25}"""
    val stream: Stream[IO, Byte] = Stream.emits(lines.getBytes("UTF-8"))
    PlayJsonBridge.parseJsonLines[IO, TestPerson](stream).compile.toList.map: results =>
      assertEquals(results.length, 3)
      assert(results(0).isRight)
      assert(results(1).isLeft)
      assert(results(2).isRight)

  test("parseJsonLines skips empty lines"):
    val lines = """{"name":"Alice","age":30}

{"name":"Bob","age":25}

"""
    val stream: Stream[IO, Byte] = Stream.emits(lines.getBytes("UTF-8"))
    PlayJsonBridge.parseJsonLines[IO, TestPerson](stream).compile.toList.map: results =>
      assertEquals(results.length, 2)
      assertEquals(results(0), Right(TestPerson("Alice", 30)))
      assertEquals(results(1), Right(TestPerson("Bob", 25)))

  test("parseJsonLines handles empty stream"):
    val stream: Stream[IO, Byte] = Stream.empty
    PlayJsonBridge.parseJsonLines[IO, TestPerson](stream).compile.toList.map: results =>
      assertEquals(results.length, 0)
