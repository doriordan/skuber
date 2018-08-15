package skuber.api

import java.net.ConnectException
import java.time.{ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Framing.FramingException
import akka.stream.scaladsl.{Flow, Keep, TcpIdleTimeoutException}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorMaterializer, KillSwitches}
import com.fasterxml.jackson.core.JsonParseException
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.specs2.mutable.Specification
import skuber.api.WatchSource.Start
import skuber.api.client.{LoggingContext, _}
import skuber.{Container, DNSPolicy, K8SRequestContext, ObjectMeta, ObjectResource, Pod, Protocol, ReplicationController, Resource, RestartPolicy}
import skuber.json.format._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class WatchSourceSpec extends Specification with MockitoSugar {
  implicit val system: ActorSystem = ActorSystem("watch-source-spec")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val loggingContext: LoggingContext = new LoggingContext {
    override def output: String = "test"
  }

  "WatchSource" should {
    "read event continuously with no name specified and from a point in time" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))
      val secondRequest = HttpRequest(uri = Uri("http://watch/2"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null)
      ).thenReturn(firstRequest)
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804")), watch = true, null)
      ).thenReturn(secondRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerFirstRequest.json"))),
        secondRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerSecondRequest.json")))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(repsonses), None, Some("12802"), 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      downstream.request(4)
        .expectNext(buildWatchEvent("12803", 3))
        .expectNext(buildWatchEvent("12804", 2))
        .expectNext(buildWatchEvent("12805", 1))
        .expectNext(buildWatchEvent("12806", 0))

      switch.shutdown()

      downstream.expectComplete()

      verify(rc, times(4)).logConfig
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null
      )
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804")), watch = true, null
      )
      ok
    }

    "read event continuously with no name specified from the beginning" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))
      val secondRequest = HttpRequest(uri = Uri("http://watch/2"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1")), watch = true, null)
      ).thenReturn(firstRequest)
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804")), watch = true, null)
      ).thenReturn(secondRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerFirstRequest.json"))),
        secondRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerSecondRequest.json")))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(repsonses), None, None, 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      downstream.request(4)
        .expectNext(buildWatchEvent("12803", 3))
        .expectNext(buildWatchEvent("12804", 2))
        .expectNext(buildWatchEvent("12805", 1))
        .expectNext(buildWatchEvent("12806", 0))

      switch.shutdown()

      downstream.expectComplete()

      verify(rc, times(4)).logConfig
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1")), watch = true, null
      )
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804")), watch = true, null
      )
      ok
    }

    "read event continuously with name specified from a point in time" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))
      val secondRequest = HttpRequest(uri = Uri("http://watch/2"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, Some("someName"), Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null)
      ).thenReturn(firstRequest)
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, Some("someName"), Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804")), watch = true, null)
      ).thenReturn(secondRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerFirstRequest.json"))),
        secondRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerSecondRequest.json")))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(repsonses), Some("someName"), Some("12802"), 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      downstream.request(4)
        .expectNext(buildWatchEvent("12803", 3))
        .expectNext(buildWatchEvent("12804", 2))
        .expectNext(buildWatchEvent("12805", 1))
        .expectNext(buildWatchEvent("12806", 0))

      switch.shutdown()

      downstream.expectComplete()

      verify(rc, times(4)).logConfig
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, Some("someName"), Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null
      )
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, Some("someName"), Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804")), watch = true, null
      )

      ok
    }

    "read event continuously with name specified from the beginning" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))
      val secondRequest = HttpRequest(uri = Uri("http://watch/2"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, Some("someName"), Some(Uri.Query("timeoutSeconds" -> "1")), watch = true, null)
      ).thenReturn(firstRequest)
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, Some("someName"), Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804")), watch = true, null)
      ).thenReturn(secondRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerFirstRequest.json"))),
        secondRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerSecondRequest.json")))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(repsonses), Some("someName"), None, 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      downstream.request(4)
        .expectNext(buildWatchEvent("12803", 3))
        .expectNext(buildWatchEvent("12804", 2))
        .expectNext(buildWatchEvent("12805", 1))
        .expectNext(buildWatchEvent("12806", 0))

      switch.shutdown()

      downstream.expectComplete()

      verify(rc, times(4)).logConfig
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, Some("someName"), Some(Uri.Query("timeoutSeconds" -> "1")), watch = true, null
      )
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, Some("someName"), Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804")), watch = true, null
      )

      ok
    }

    "handle empty responses from the cluster when request timeout times out" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))
      val secondRequest = HttpRequest(uri = Uri("http://watch/2"))
      val thirdRequest = HttpRequest(uri = Uri("http://watch/3"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null)
      ).thenReturn(firstRequest)
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804")), watch = true, null)
      ).thenReturn(secondRequest).thenReturn(thirdRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerFirstRequest.json"))),
        secondRequest -> HttpResponse(StatusCodes.OK, entity = ""),
        thirdRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerSecondRequest.json")))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(repsonses), None, Some("12802"), 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      downstream.request(4)
        .expectNext(buildWatchEvent("12803", 3))
        .expectNext(buildWatchEvent("12804", 2))
        .expectNext(buildWatchEvent("12805", 1))
        .expectNext(buildWatchEvent("12806", 0))

      switch.shutdown()

      downstream.expectComplete()

      verify(rc, times(6)).logConfig
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null
      )
      verify(rc, times(2)).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804")), watch = true, null
      )
      ok
    }

    "handle bad input from cluster" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null)
      ).thenReturn(firstRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = "bad input")
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(repsonses), None, Some("12802"), 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[FramingException]
      error.getMessage mustEqual "Invalid JSON encountered at position [0] of [ByteString(98, 97, 100, 32, 105, 110, 112, 117, 116)]"

      verify(rc, times(2)).logConfig
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null
      )

      ok
    }

    "handle bad json from cluster" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null)
      ).thenReturn(firstRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity("{asdf:asdfa}"))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(repsonses), None, Some("12802"), 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[JsonParseException]
      error.getMessage mustEqual "Unexpected character ('a' (code 97)): was expecting double-quote to start field name\n at [Source: {asdf:asdfa}; line: 1, column: 3]"

      verify(rc, times(2)).logConfig
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null
      )

      ok
    }

    "handle a HTTP 500 error from service" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null)
      ).thenReturn(firstRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.InternalServerError)
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(repsonses), None, Some("12802"), 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[K8SException]
      error.asInstanceOf[K8SException].status mustEqual Status(message = Some("Error watching resource."), code = Some(500))

      verify(rc).logConfig
      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null
      )

      ok
    }

    "handle a HTTP 403 error from service" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null)
      ).thenReturn(firstRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.Unauthorized)
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(repsonses), None, Some("12802"), 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[K8SException]
      error.asInstanceOf[K8SException].status mustEqual Status(message = Some("Error watching resource."), code = Some(401))

      verify(rc).logConfig

      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null
      )

      ok
    }

    "handle idle timeout from service" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null)
      ).thenReturn(firstRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.InternalServerError)
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(new TcpIdleTimeoutException("timeout", 10.seconds)), None, Some("12802"), 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[K8SException]
      error.asInstanceOf[K8SException].status mustEqual Status(message = Some("Error watching resource."))

      verify(rc).logConfig

      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null
      )

      ok
    }

    "handle connection timeout from service" >> {
      val rc = mock[K8SRequestContext]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(rc.logConfig).thenReturn(LoggingConfig())
      when(rc.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null)
      ).thenReturn(firstRequest)

      val repsonses = Map(
        firstRequest -> HttpResponse(StatusCodes.InternalServerError)
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](rc, mockPool(new ConnectException(s"Connect timeout of 10s expired")), None, Some("12802"), 1.second, 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[K8SException]
      error.asInstanceOf[K8SException].status mustEqual Status(message = Some("Error watching resource."))

      verify(rc).logConfig

      verify(rc).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802")), watch = true, null
      )

      ok
    }
  }

  private def buildWatchEvent(resourceVersion: String, replicas: Int) = {
    WatchEvent(
      EventType.MODIFIED,
      ReplicationController(
        metadata = ObjectMeta(
          "frontend", "", "default", "246f12b6-719b-11e5-89ae-0800279dd272",
          "/api/v1/namespaces/default/replicationcontrollers/frontend", resourceVersion,
          Some(ZonedDateTime.of(2015, 10, 13, 11, 11, 24, 0, ZoneId.of("Z"))),
          None, None, Map("name" -> "frontend"), Map(), Nil, 2, None, None
        ),
        spec = Some(ReplicationController.Spec(
          0, Some(Map("name" -> "frontend")), Some(
            Pod.Template.Spec(
              ObjectMeta(
                "frontend", "", "default", "", "", "", None,
                None, None, Map("name" -> "frontend"), Map(),
                Nil, 0, None, None
              ),
              Some(
                Pod.Spec(
                  containers = List(
                    Container(
                      "php-redis",
                      "kubernetes/example-guestbook-php-redis:v2",
                      ports = List(
                        Container.Port(
                          80, Protocol.TCP
                        )
                      ),
                      resources = Some(Resource.Requirements()),
                      terminationMessagePath = Some("/var/log/termination"),
                      imagePullPolicy = Container.PullPolicy.IfNotPresent
                    )
                  ),
                  restartPolicy = RestartPolicy.Always,
                  dnsPolicy = DNSPolicy.Default
                )
              )
            )
          )
        )),
        status = Some(ReplicationController.Status(replicas, None))
      )
    )
  }

  def mockPool[O <: ObjectResource](requestResponses: Map[HttpRequest, HttpResponse]): Pool[Start[O]] = {
    Flow[(HttpRequest, Start[O])].map { x =>
      (Try(requestResponses(x._1)), x._2)
    }
  }

  def mockPool[O <: ObjectResource](error: Throwable): Pool[Start[O]] = {
    Flow[(HttpRequest, Start[O])].map { x =>
      (Try(throw error), x._2)
    }
  }

  def retrieveWatchJson(path: String): String = {
    val source = scala.io.Source.fromURL(getClass.getResource(path))
    try source.mkString finally source.close()
  }

  def createHttpEntity(json: String): HttpEntity.Strict = {
    Await.result(HttpEntity(MediaTypes.`application/json`, json).toStrict(5.seconds), 5.seconds)
  }
}
