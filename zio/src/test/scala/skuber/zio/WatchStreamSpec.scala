package skuber.zio

import zio.*
import zio.stream.*
import zio.test.*
import skuber.api.client.*
import skuber.internal.*
import skuber.zio.internal.{HttpBackend, ZKubernetesClientImpl}
import skuber.model.*
import skuber.json.format.podFormat

object WatchStreamSpec extends ZIOSpecDefault:

  private val watchEventJson1 =
    """{"type":"ADDED","object":{"kind":"Pod","apiVersion":"v1","metadata":{"name":"pod-1","namespace":"default","resourceVersion":"100"}}}"""
  private val watchEventJson2 =
    """{"type":"MODIFIED","object":{"kind":"Pod","apiVersion":"v1","metadata":{"name":"pod-1","namespace":"default","resourceVersion":"101"}}}"""

  private def streamingBackend(ndjson: String, callsBeforeEmpty: Int = 1): HttpBackend =
    new HttpBackend:
      private val callCount = java.util.concurrent.atomic.AtomicInteger(0)
      override def request(req: K8sRequest): IO[Throwable, K8sResponse] =
        ZIO.succeed(K8sResponse(200, Array.emptyByteArray))
      override def streamRequest(req: K8sRequest): ZStream[Any, Throwable, Byte] =
        if callCount.getAndIncrement() < callsBeforeEmpty then
          ZStream.fromIterable(ndjson.getBytes("UTF-8"))
        else ZStream.empty
      override def websocket(req: K8sRequest, stdin: Option[ZStream[Any, Nothing, Array[Byte]]]): ZStream[Any, Throwable, WebSocketMessage] =
        ZStream.empty

  private def makeClient(backend: HttpBackend): ZKubernetesClient =
    ZKubernetesClientImpl(backend = backend, clusterServer = "https://kubernetes.local", auth = NoAuth, namespace = "default")

  def spec = suite("WatchStream")(
    test("parses newline-delimited watch events") {
      val ndjson  = s"$watchEventJson1\n$watchEventJson2\n"
      val client  = makeClient(streamingBackend(ndjson))
      client.watch[Pod]().take(2).runCollect.map: events =>
        assertTrue(events.size == 2)
        assertTrue(events(0)._type == EventType.ADDED)
        assertTrue(events(0)._object.name == "pod-1")
        assertTrue(events(1)._type == EventType.MODIFIED)
    },
    test("skips malformed lines and continues") {
      val ndjson = s"$watchEventJson1\n{invalid json}\n$watchEventJson2\n"
      val client = makeClient(streamingBackend(ndjson))
      client.watch[Pod]().take(2).runCollect.map: events =>
        assertTrue(events.size == 2)
    },
    test("reconnects after empty stream") {
      val ndjson = s"$watchEventJson1\n$watchEventJson2\n"
      val client = makeClient(streamingBackend(ndjson, callsBeforeEmpty = 1))
      client.watch[Pod]().take(2).runCollect.map: events =>
        assertTrue(events.size == 2)
    },
    test("passes watch=true query param") {
      for
        capturedRef <- Ref.make(Option.empty[K8sRequest])
        backend = new HttpBackend:
          override def request(req: K8sRequest): IO[Throwable, K8sResponse] =
            ZIO.succeed(K8sResponse(200, Array.emptyByteArray))
          override def streamRequest(req: K8sRequest): ZStream[Any, Throwable, Byte] =
            ZStream.fromZIO(capturedRef.set(Some(req))) *> ZStream.fromIterable(s"$watchEventJson1\n".getBytes("UTF-8"))
          override def websocket(req: K8sRequest, stdin: Option[ZStream[Any, Nothing, Array[Byte]]]): ZStream[Any, Throwable, WebSocketMessage] =
            ZStream.empty
        client = makeClient(backend)
        _       <- client.watch[Pod](WatchParameters(resourceVersion = Some("42"), timeoutSeconds = Some(30))).take(1).runDrain
        captured <- capturedRef.get
      yield
        val qs = captured.get.queryParams
        assertTrue(qs.exists(_ == ("watch", "true")))
        assertTrue(qs.exists(_ == ("resourceVersion", "42")))
        assertTrue(qs.exists(_ == ("timeoutSeconds", "30")))
    }
  )
