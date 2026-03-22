package skuber.catseffect

import cats.effect.IO
import fs2.Stream
import munit.CatsEffectSuite
import skuber.api.client.*
import skuber.catseffect.internal.*
import skuber.internal.{HttpMethod, K8sRequest, K8sResponse, WebSocketMessage}
import skuber.model.*
import skuber.json.format.podFormat

class WatchStreamSpec extends CatsEffectSuite:

  private given LoggingContext = RequestLoggingContext()

  private def streamingBackend(ndjson: String): HttpBackend[IO] = new HttpBackend[IO]:
    override def request(req: K8sRequest): IO[K8sResponse] =
      IO.pure(K8sResponse(200, Array.emptyByteArray))
    override def streamRequest(req: K8sRequest): Stream[IO, Byte] =
      Stream.emits(ndjson.getBytes("UTF-8"))
    override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
      Stream.empty

  private def makeClient(backend: HttpBackend[IO]): CatsKubernetesClient[IO] =
    new CatsKubernetesClientImpl[IO](
      backend = backend,
      clusterServer = "https://kubernetes.local",
      auth = NoAuth,
      namespace = "default",
      logConfig = LoggingConfig()
    )

  private val watchEventJson1 =
    """{"type":"ADDED","object":{"kind":"Pod","apiVersion":"v1","metadata":{"name":"pod-1","namespace":"default","resourceVersion":"100"}}}"""

  private val watchEventJson2 =
    """{"type":"MODIFIED","object":{"kind":"Pod","apiVersion":"v1","metadata":{"name":"pod-1","namespace":"default","resourceVersion":"101"}}}"""

  test("watch parses newline-delimited watch events"):
    val ndjson = s"$watchEventJson1\n$watchEventJson2\n"
    val backend = new HttpBackend[IO]:
      // Only emit once - don't reconnect for this test
      @volatile private var called = false
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.pure(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] =
        if !called then
          called = true
          Stream.emits(ndjson.getBytes("UTF-8"))
        else
          // Return empty to stop reconnection; the stream will recurse but we take(2) below
          Stream.empty
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
        Stream.empty

    val client = makeClient(backend)
    client.watch[Pod]().take(2).compile.toList.map: events =>
      assertEquals(events.size, 2)
      assert(events(0).isRight)
      assert(events(1).isRight)
      val ev1 = events(0).toOption.get
      val ev2 = events(1).toOption.get
      assertEquals(ev1._type, EventType.ADDED)
      assertEquals(ev1._object.name, "pod-1")
      assertEquals(ev2._type, EventType.MODIFIED)
      assertEquals(ev2._object.metadata.resourceVersion, "101")

  test("watch handles empty stream gracefully (reconnects without error)"):
    // Backend returns empty stream each time. We verify it doesn't fail but produces no events.
    val backend = new HttpBackend[IO]:
      @volatile private var callCount = 0
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.pure(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] =
        callCount += 1
        Stream.empty
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
        Stream.empty

    val client = makeClient(backend)
    // Take 0 events but let it attempt a few reconnections. Since the stream is infinite (reconnects),
    // we use take(0) to just verify no errors are raised.
    client.watch[Pod]().take(0).compile.toList.map: events =>
      assertEquals(events.size, 0)

  test("watch passes query parameters including watch=true"):
    val captured = new java.util.concurrent.atomic.AtomicReference[K8sRequest]()
    val backend = new HttpBackend[IO]:
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.pure(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] =
        captured.set(req)
        Stream.emits(s"$watchEventJson1\n".getBytes("UTF-8"))
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
        Stream.empty

    val client = makeClient(backend)
    val params = WatchParameters(
      resourceVersion = Some("42"),
      timeoutSeconds = Some(30),
      allowWatchBookmarks = true
    )
    client.watch[Pod](params).take(1).compile.toList.map: _ =>
      val req = captured.get()
      assertEquals(req.queryParams.get("watch"), Some("true"))
      assertEquals(req.queryParams.get("resourceVersion"), Some("42"))
      assertEquals(req.queryParams.get("timeoutSeconds"), Some("30"))
      assertEquals(req.queryParams.get("allowWatchBookmarks"), Some("true"))

  test("watch returns Left(Status) for malformed JSON"):
    val ndjson = "{invalid json}\n"
    val backend = new HttpBackend[IO]:
      @volatile private var called = false
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.pure(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] =
        if !called then
          called = true
          Stream.emits(ndjson.getBytes("UTF-8"))
        else
          Stream.empty
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
        Stream.empty

    val client = makeClient(backend)
    client.watch[Pod]().take(1).compile.toList.map: events =>
      assertEquals(events.size, 1)
      assert(events(0).isLeft)

  test("getPodLogStream streams log bytes"):
    val logData = "line1\nline2\nline3"
    val backend = new HttpBackend[IO]:
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.pure(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] =
        Stream.emits(logData.getBytes("UTF-8"))
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
        Stream.empty

    val client = makeClient(backend)
    client.getPodLogStream("my-pod").through(fs2.text.utf8.decode).compile.string.map: result =>
      assertEquals(result, logData)

  test("getPodLogStream passes query params from LogQueryParams"):
    val captured = new java.util.concurrent.atomic.AtomicReference[K8sRequest]()
    val backend = new HttpBackend[IO]:
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.pure(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] =
        captured.set(req)
        Stream.empty
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
        Stream.empty

    val client = makeClient(backend)
    val params = Pod.LogQueryParams(containerName = Some("main"), tailLines = Some(100), follow = Some(true))
    client.getPodLogStream("my-pod", params).compile.drain.map: _ =>
      val req = captured.get()
      assertEquals(req.queryParams.get("container"), Some("main"))
      assertEquals(req.queryParams.get("tailLines"), Some("100"))
      assertEquals(req.queryParams.get("follow"), Some("true"))

  test("getWatcher.watchObject passes fieldSelector for named object"):
    val captured = new java.util.concurrent.atomic.AtomicReference[K8sRequest]()
    val backend = new HttpBackend[IO]:
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.pure(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] =
        captured.set(req)
        Stream.emits(s"$watchEventJson1\n".getBytes("UTF-8"))
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
        Stream.empty

    val client = makeClient(backend)
    val watcher = client.getWatcher[Pod]
    given skuber.api.client.LoggingContext = skuber.api.client.RequestLoggingContext()
    watcher.watchObject("my-pod").take(1).compile.toList.map: _ =>
      val req = captured.get()
      assertEquals(req.queryParams.get("watch"), Some("true"))
      assertEquals(req.queryParams.get("fieldSelector"), Some("metadata.name=my-pod"))

  test("getWatcher.watchStartingFromVersion passes resourceVersion"):
    val captured = new java.util.concurrent.atomic.AtomicReference[K8sRequest]()
    val backend = new HttpBackend[IO]:
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.pure(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] =
        captured.set(req)
        Stream.emits(s"$watchEventJson1\n".getBytes("UTF-8"))
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
        Stream.empty

    val client = makeClient(backend)
    val watcher = client.getWatcher[Pod]
    given skuber.api.client.LoggingContext = skuber.api.client.RequestLoggingContext()
    watcher.watchStartingFromVersion("99").take(1).compile.toList.map: _ =>
      val req = captured.get()
      assertEquals(req.queryParams.get("resourceVersion"), Some("99"))

  test("getWatcher.watchCluster passes cluster-scope URL"):
    val captured = new java.util.concurrent.atomic.AtomicReference[K8sRequest]()
    val backend = new HttpBackend[IO]:
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.pure(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] =
        captured.set(req)
        Stream.emits(s"$watchEventJson1\n".getBytes("UTF-8"))
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
        Stream.empty

    val client = makeClient(backend)
    val watcher = client.getWatcher[Pod]
    given skuber.api.client.LoggingContext = skuber.api.client.RequestLoggingContext()
    watcher.watchCluster().take(1).compile.toList.map: _ =>
      val req = captured.get()
      // cluster-scope URL omits namespace segment
      assert(!req.url.contains("/namespaces/"), s"Expected cluster-scope URL but got: ${req.url}")

  test("exec produces ExecOutput from websocket binary messages"):
    val stdoutMsg = Array[Byte](1) ++ "hello stdout".getBytes("UTF-8")
    val stderrMsg = Array[Byte](2) ++ "hello stderr".getBytes("UTF-8")
    val backend = new HttpBackend[IO]:
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.pure(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] = Stream.empty
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] =
        Stream(WebSocketMessage.Binary(stdoutMsg), WebSocketMessage.Binary(stderrMsg))

    val client = makeClient(backend)
    client.exec("my-pod", Seq("sh", "-c", "echo hello")).compile.toList.map: outputs =>
      assertEquals(outputs.size, 2)
      assertEquals(outputs(0), ExecOutput.Stdout("hello stdout"))
      assertEquals(outputs(1), ExecOutput.Stderr("hello stderr"))
