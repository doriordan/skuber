package skuber.zio

import zio.*
import zio.stream.*
import zio.test.*
import skuber.api.client.*
import skuber.internal.*
import skuber.zio.internal.{HttpBackend, ZKubernetesClientImpl}
import skuber.model.*
import play.api.libs.json.Json

object ZKubernetesClientSpec extends ZIOSpecDefault:

  private def mockBackend(statusCode: Int, responseBody: String): HttpBackend = new HttpBackend:
    override def request(req: K8sRequest): IO[Throwable, K8sResponse] =
      ZIO.succeed(K8sResponse(statusCode, responseBody.getBytes("UTF-8")))
    override def streamRequest(req: K8sRequest): ZStream[Any, Throwable, Byte] =
      ZStream.empty
    override def websocket(req: K8sRequest, stdin: Option[ZStream[Any, Nothing, Array[Byte]]]): ZStream[Any, Throwable, WebSocketMessage] =
      ZStream.empty

  private def makeClient(backend: HttpBackend, ns: String = "default"): ZKubernetesClient =
    ZKubernetesClientImpl(
      backend = backend,
      clusterServer = "https://kubernetes.local",
      auth = NoAuth,
      namespace = ns
    )

  private val testPodJson = """{"kind":"Pod","apiVersion":"v1","metadata":{"name":"test-pod","namespace":"default"}}"""
  private val notFoundStatusJson = """{"apiVersion":"v1","kind":"Status","metadata":{},"status":"Failure","message":"not found","reason":"NotFound","code":404}"""
  private val forbiddenStatusJson = """{"apiVersion":"v1","kind":"Status","metadata":{},"status":"Failure","message":"forbidden","reason":"Forbidden","code":403}"""
  private val conflictStatusJson = """{"apiVersion":"v1","kind":"Status","metadata":{},"status":"Failure","message":"already exists","reason":"AlreadyExists","code":409}"""

  def spec = suite("ZKubernetesClient")(
    test("get succeeds on 200 with valid Pod JSON") {
      import skuber.json.format.podFormat
      val client = makeClient(mockBackend(200, testPodJson))
      client.get[Pod]("test-pod").map: pod =>
        assertTrue(pod.name == "test-pod")
    },
    test("get fails with K8SException on 404") {
      import skuber.json.format.podFormat
      val client = makeClient(mockBackend(404, notFoundStatusJson))
      client.get[Pod]("test-pod").exit.map: result =>
        assertTrue(result.isFailure)
        assertTrue(result.causeOption.flatMap(_.failureOption).exists(_.isNotFound))
    },
    test("getOption returns None on 404") {
      import skuber.json.format.podFormat
      val client = makeClient(mockBackend(404, notFoundStatusJson))
      client.getOption[Pod]("test-pod").map: opt =>
        assertTrue(opt.isEmpty)
    },
    test("getOption returns Some on 200") {
      import skuber.json.format.podFormat
      val client = makeClient(mockBackend(200, testPodJson))
      client.getOption[Pod]("test-pod").map: opt =>
        assertTrue(opt.isDefined)
        assertTrue(opt.get.name == "test-pod")
    },
    test("getOption fails with K8SException on non-404 error") {
      import skuber.json.format.podFormat
      val client = makeClient(mockBackend(403, forbiddenStatusJson))
      client.getOption[Pod]("test-pod").exit.map: result =>
        assertTrue(result.isFailure)
    },
    test("create succeeds on 201") {
      import skuber.json.format.podFormat
      val client = makeClient(mockBackend(201, testPodJson))
      val pod = Pod(metadata = ObjectMeta(name = "test-pod", namespace = "default"))
      client.create[Pod](pod).map: created =>
        assertTrue(created.name == "test-pod")
    },
    test("create fails with K8SException on 409") {
      import skuber.json.format.podFormat
      val client = makeClient(mockBackend(409, conflictStatusJson))
      val pod = Pod(metadata = ObjectMeta(name = "test-pod", namespace = "default"))
      client.create[Pod](pod).exit.map: result =>
        assertTrue(result.isFailure)
        assertTrue(result.causeOption.flatMap(_.failureOption).exists(_.isConflict))
    },
    test("delete succeeds on 200") {
      val client = makeClient(mockBackend(200, """{"kind":"Status","status":"Success"}"""))
      client.delete[Pod]("test-pod").map: _ =>
        assertTrue(true)
    },
    test("delete fails with K8SException on 404") {
      val client = makeClient(mockBackend(404, notFoundStatusJson))
      client.delete[Pod]("test-pod").exit.map: result =>
        assertTrue(result.isFailure)
    },
    test("usingNamespace sends request to different namespace") {
      for
        capturedRef <- Ref.make(Option.empty[K8sRequest])
        backend = new HttpBackend:
          override def request(req: K8sRequest): IO[Throwable, K8sResponse] =
            capturedRef.set(Some(req)) *> ZIO.succeed(K8sResponse(200, testPodJson.getBytes("UTF-8")))
          override def streamRequest(req: K8sRequest): ZStream[Any, Throwable, Byte] = ZStream.empty
          override def websocket(req: K8sRequest, stdin: Option[ZStream[Any, Nothing, Array[Byte]]]): ZStream[Any, Throwable, WebSocketMessage] = ZStream.empty
        client = makeClient(backend, "default").usingNamespace("production")
        _ <- { import skuber.json.format.podFormat; client.get[Pod]("test-pod") }
        captured <- capturedRef.get
      yield assertTrue(captured.exists(_.url.contains("namespaces/production")))
    },
    test("getServerAPIVersions returns version list") {
      val responseJson = """{"kind":"APIVersions","versions":["v1"],"serverAddressByClientCIDRs":[]}"""
      val client = makeClient(mockBackend(200, responseJson))
      client.getServerAPIVersions.map: versions =>
        assertTrue(versions == List("v1"))
    },
    test("exec produces ExecOutput from websocket binary messages") {
      val stdoutMsg = Array[Byte](1) ++ "hello stdout".getBytes("UTF-8")
      val stderrMsg = Array[Byte](2) ++ "hello stderr".getBytes("UTF-8")
      val backend = new HttpBackend:
        override def request(req: K8sRequest): IO[Throwable, K8sResponse] =
          ZIO.succeed(K8sResponse(200, Array.emptyByteArray))
        override def streamRequest(req: K8sRequest): ZStream[Any, Throwable, Byte] =
          ZStream.empty
        override def websocket(req: K8sRequest, stdin: Option[ZStream[Any, Nothing, Array[Byte]]]): ZStream[Any, Throwable, WebSocketMessage] =
          ZStream(WebSocketMessage.Binary(stdoutMsg), WebSocketMessage.Binary(stderrMsg))
      val client = makeClient(backend)
      client.exec("my-pod", Seq("sh", "-c", "echo hello")).runCollect.map: outputs =>
        assertTrue(outputs.size == 2)
        assertTrue(outputs(0) == ExecOutput.Stdout("hello stdout"))
        assertTrue(outputs(1) == ExecOutput.Stderr("hello stderr"))
    },
    test("getPodLogStream streams bytes") {
      val logData = "line1\nline2"
      val backend = new HttpBackend:
        override def request(req: K8sRequest): IO[Throwable, K8sResponse] =
          ZIO.succeed(K8sResponse(200, Array.emptyByteArray))
        override def streamRequest(req: K8sRequest): ZStream[Any, Throwable, Byte] =
          ZStream.fromIterable(logData.getBytes("UTF-8"))
        override def websocket(req: K8sRequest, stdin: Option[ZStream[Any, Nothing, Array[Byte]]]): ZStream[Any, Throwable, WebSocketMessage] =
          ZStream.empty
      val client = makeClient(backend)
      client.getPodLogStream("my-pod").runCollect.map: bytes =>
        assertTrue(new String(bytes.toArray, "UTF-8") == logData)
    },
    test("exec encodes command as repeated queryParams") {
      for
        capturedRef <- Ref.make(Option.empty[K8sRequest])
        backend = new HttpBackend:
          override def request(req: K8sRequest): IO[Throwable, K8sResponse] =
            ZIO.succeed(K8sResponse(200, Array.emptyByteArray))
          override def streamRequest(req: K8sRequest): ZStream[Any, Throwable, Byte] = ZStream.empty
          override def websocket(req: K8sRequest, stdin: Option[ZStream[Any, Nothing, Array[Byte]]]): ZStream[Any, Throwable, WebSocketMessage] =
            ZStream.fromZIO(capturedRef.set(Some(req))) *> ZStream.empty
        client = makeClient(backend)
        _       <- client.exec("my-pod", Seq("ls", "-la")).runDrain
        captured <- capturedRef.get
      yield
        val qs = captured.get.queryParams
        val commands = qs.collect { case ("command", v) => v }
        assertTrue(commands == Seq("ls", "-la"))
    }
  )
