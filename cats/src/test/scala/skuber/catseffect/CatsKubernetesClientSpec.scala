package skuber.catseffect

import cats.effect.IO
import fs2.Stream
import munit.CatsEffectSuite
import play.api.libs.json.Json
import skuber.api.client.*
import skuber.catseffect.internal.*
import skuber.internal.{HttpMethod, K8sRequest, K8sResponse, WebSocketMessage}
import skuber.model.*

extension (qs: Seq[(String, String)])
  def get(key: String): Option[String] = qs.find(_._1 == key).map(_._2)

class CatsKubernetesClientSpec extends CatsEffectSuite:

  private def mockBackend(statusCode: Int, responseBody: String): HttpBackend[IO] = new HttpBackend[IO]:
    override def request(req: K8sRequest): IO[K8sResponse] =
      IO.pure(K8sResponse(statusCode, responseBody.getBytes("UTF-8")))
    override def streamRequest(req: K8sRequest): Stream[IO, Byte] = Stream.empty
    override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] = Stream.empty

  private def capturingBackend(statusCode: Int, responseBody: String): (HttpBackend[IO], java.util.concurrent.atomic.AtomicReference[K8sRequest]) =
    val captured = new java.util.concurrent.atomic.AtomicReference[K8sRequest]()
    val backend = new HttpBackend[IO]:
      override def request(req: K8sRequest): IO[K8sResponse] =
        IO.delay(captured.set(req)) *> IO.pure(K8sResponse(statusCode, responseBody.getBytes("UTF-8")))
      override def streamRequest(req: K8sRequest): Stream[IO, Byte] = Stream.empty
      override def websocket(req: K8sRequest, stdin: Option[Stream[IO, Array[Byte]]]): Stream[IO, WebSocketMessage] = Stream.empty
    (backend, captured)

  private def makeClient(backend: HttpBackend[IO], ns: String = "default"): CatsKubernetesClient[IO] =
    new CatsKubernetesClientImpl[IO](
      backend = backend,
      clusterServer = "https://kubernetes.local",
      auth = NoAuth,
      namespace = ns,
      logConfig = LoggingConfig()
    )

  private given LoggingContext = RequestLoggingContext()

  // Construct Pod JSON manually to avoid triggering eager initialization of the entire format package
  private val testPodJson = """{"kind":"Pod","apiVersion":"v1","metadata":{"name":"test-pod","namespace":"default"}}"""
  private val notFoundStatusJson = """{"apiVersion":"v1","kind":"Status","metadata":{},"status":"Failure","message":"not found","reason":"NotFound","code":404}"""
  private val forbiddenStatusJson = """{"apiVersion":"v1","kind":"Status","metadata":{},"status":"Failure","message":"forbidden","reason":"Forbidden","code":403}"""
  private val conflictStatusJson = """{"apiVersion":"v1","kind":"Status","metadata":{},"status":"Failure","message":"already exists","reason":"AlreadyExists","code":409}"""
  private val successStatusJson = """{"apiVersion":"v1","kind":"Status","metadata":{},"status":"Success","code":200}"""

  test("get returns Right on 200 with valid Pod JSON"):
    val client = makeClient(mockBackend(200, testPodJson))
    import skuber.json.format.podFormat
    client.get[Pod]("test-pod").map: result =>
      assert(result.isRight)
      assertEquals(result.toOption.get.name, "test-pod")

  test("get returns Left(K8SException) on 404"):
    val client = makeClient(mockBackend(404, notFoundStatusJson))
    import skuber.json.format.podFormat
    client.get[Pod]("test-pod").map: result =>
      assert(result.isLeft)
      assertEquals(result.left.toOption.get.code, Some(404))

  test("getOption returns None on 404"):
    val client = makeClient(mockBackend(404, notFoundStatusJson))
    import skuber.json.format.podFormat
    client.getOption[Pod]("test-pod").map: result =>
      assertEquals(result, None)

  test("getOption returns Some on 200"):
    val client = makeClient(mockBackend(200, testPodJson))
    import skuber.json.format.podFormat
    client.getOption[Pod]("test-pod").map: result =>
      assert(result.isDefined)
      assertEquals(result.get.name, "test-pod")

  test("getOption raises K8SException on non-404 error"):
    val client = makeClient(mockBackend(403, forbiddenStatusJson))
    import skuber.json.format.podFormat
    client.getOption[Pod]("test-pod").attempt.map: result =>
      assert(result.isLeft)
      assert(result.left.toOption.get.isInstanceOf[K8SException])

  test("create returns Right on 201"):
    val client = makeClient(mockBackend(201, testPodJson))
    import skuber.json.format.podFormat
    val pod = Pod(metadata = ObjectMeta(name = "test-pod", namespace = "default"))
    client.create[Pod](pod).map: result =>
      assert(result.isRight)
      assertEquals(result.toOption.get.name, "test-pod")

  test("create returns Left on 409 Conflict"):
    val client = makeClient(mockBackend(409, conflictStatusJson))
    import skuber.json.format.podFormat
    val pod = Pod(metadata = ObjectMeta(name = "test-pod", namespace = "default"))
    client.create[Pod](pod).map: result =>
      assert(result.isLeft)
      assertEquals(result.left.toOption.get.code, Some(409))

  test("update returns Right on 200"):
    val client = makeClient(mockBackend(200, testPodJson))
    import skuber.json.format.podFormat
    val pod = Pod(metadata = ObjectMeta(name = "test-pod", namespace = "default"))
    client.update[Pod](pod).map: result =>
      assert(result.isRight)
      assertEquals(result.toOption.get.name, "test-pod")

  test("delete returns Right(()) on 200"):
    val client = makeClient(mockBackend(200, successStatusJson))
    client.delete[Pod]("test-pod").map: result =>
      assert(result.isRight)
      assertEquals(result.toOption.get, ())

  test("delete returns Left on 404"):
    val client = makeClient(mockBackend(404, notFoundStatusJson))
    client.delete[Pod]("test-pod").map: result =>
      assert(result.isLeft)

  test("usingNamespace returns new client with different namespace"):
    val (backend, captured) = capturingBackend(200, testPodJson)
    val client = makeClient(backend, "default")
    val nsClient = client.usingNamespace("other-ns")
    import skuber.json.format.podFormat
    nsClient.get[Pod]("test-pod").map: _ =>
      val req = captured.get()
      assert(req.url.contains("namespaces/other-ns"), s"Expected URL to contain 'namespaces/other-ns', got: ${req.url}")

  test("create sends POST request with JSON body"):
    val (backend, captured) = capturingBackend(201, testPodJson)
    val client = makeClient(backend)
    import skuber.json.format.podFormat
    val pod = Pod(metadata = ObjectMeta(name = "test-pod", namespace = "default"))
    client.create[Pod](pod).map: _ =>
      val req = captured.get()
      assertEquals(req.method, HttpMethod.Post)
      assert(req.body.isDefined)
      assertEquals(req.headers.get("Content-Type"), Some("application/json"))

  test("update sends PUT request"):
    val (backend, captured) = capturingBackend(200, testPodJson)
    val client = makeClient(backend)
    import skuber.json.format.podFormat
    val pod = Pod(metadata = ObjectMeta(name = "test-pod", namespace = "default"))
    client.update[Pod](pod).map: _ =>
      val req = captured.get()
      assertEquals(req.method, HttpMethod.Put)

  test("delete sends DELETE request"):
    val (backend, captured) = capturingBackend(200, successStatusJson)
    val client = makeClient(backend)
    client.delete[Pod]("test-pod").map: _ =>
      val req = captured.get()
      assertEquals(req.method, HttpMethod.Delete)

  test("getServerAPIVersions returns list of versions"):
    val responseJson = """{"kind":"APIVersions","versions":["v1"],"serverAddressByClientCIDRs":[]}"""
    val client = makeClient(mockBackend(200, responseJson))
    client.getServerAPIVersions.map: result =>
      assert(result.isRight)
      assertEquals(result.toOption.get, List("v1"))

  test("listWithOptions passes query params"):
    val podListJson = """{"apiVersion":"v1","kind":"PodList","metadata":{},"items":[]}"""
    val (backend, captured) = capturingBackend(200, podListJson)
    val client = makeClient(backend)
    import skuber.json.format.podListFmt
    val options = ListOptions(fieldSelector = Some("status.phase=Running"), limit = Some(10))
    client.listWithOptions[PodList](options).map: _ =>
      val req = captured.get()
      assertEquals(req.queryParams.get("fieldSelector"), Some("status.phase=Running"))
      assertEquals(req.queryParams.get("limit"), Some("10"))

  test("watch stream returns empty when backend stream is empty"):
    val client = makeClient(mockBackend(200, ""))
    import skuber.json.format.podFormat
    client.watch[Pod]().take(0).compile.toList.map: result =>
      assertEquals(result.size, 0)

  test("getPodLogStream returns empty when backend stream is empty"):
    val client = makeClient(mockBackend(200, ""))
    client.getPodLogStream("test-pod").compile.toList.map: result =>
      assertEquals(result.size, 0)

  test("exec returns empty when backend websocket is empty"):
    val client = makeClient(mockBackend(200, ""))
    client.exec("test-pod", Seq("ls")).compile.toList.map: result =>
      assertEquals(result.size, 0)
