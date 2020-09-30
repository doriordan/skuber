package skuber.api

import java.net.ConnectException
import java.time.{ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Framing.FramingException
import akka.stream.scaladsl.{Flow, Keep, TcpIdleTimeoutException}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.KillSwitches
import com.fasterxml.jackson.core.JsonParseException
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.specs2.mutable.Specification
import skuber.api.client.impl.KubernetesClientImpl
import skuber.api.watch.WatchSource.Start
import skuber.api.client.{LoggingContext, _}
import skuber.api.watch.WatchSource
import skuber.{Container, DNSPolicy, ListOptions, ObjectMeta, ObjectResource, Pod, Protocol, ReplicationController, Resource, RestartPolicy}
import skuber.json.format._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class WatchSourceSpec extends Specification with MockitoSugar {
  implicit val system: ActorSystem = ActorSystem("watch-source-spec")
  implicit val loggingContext: LoggingContext = new LoggingContext {
    override def output: String = "test"
  }

  "WatchSource" should {
    "read event continuously with no name specified and from a point in time" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))
      val secondRequest = HttpRequest(uri = Uri("http://watch/2"))

      when(client.logConfig).thenReturn(LoggingConfig())
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null)
      ).thenReturn(firstRequest)
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804", "watch" -> "true")), null)
      ).thenReturn(secondRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerFirstRequest.json"))),
        secondRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerSecondRequest.json")))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(responses), None, ListOptions(resourceVersion=Some("12802"), timeoutSeconds=Some(1)), 10000)
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

      verify(client, times(4)).logConfig
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802",  "watch" -> "true")), null
      )
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804", "watch" -> "true")), null
      )
      ok
    }

    "read event continuously with no name specified from the beginning" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))
      val secondRequest = HttpRequest(uri = Uri("http://watch/2"))

      when(client.logConfig).thenReturn(LoggingConfig())
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "watch" -> "true")), null)
      ).thenReturn(firstRequest)
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804", "watch" -> "true")), null)
      ).thenReturn(secondRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerFirstRequest.json"))),
        secondRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerSecondRequest.json")))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(responses), None, ListOptions(timeoutSeconds=Some(1)), 10000)
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

      verify(client, times(4)).logConfig
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "watch" -> "true")), null
      )
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804", "watch" -> "true")), null
      )
      ok
    }

    "read event continuously with name specified from a point in time" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))
      val secondRequest = HttpRequest(uri = Uri("http://watch/2"))
      val name = "someName"
      val nameFieldSelector=s"metadata.name=$name"
      val query1=Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true", "fieldSelector" -> nameFieldSelector)
      val query2=Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804", "watch" -> "true", "fieldSelector" -> nameFieldSelector)

      when(client.logConfig).thenReturn(LoggingConfig())

      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(query1), null)
      ).thenReturn(firstRequest)
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(query2), null)
      ).thenReturn(secondRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerFirstRequest.json"))),
        secondRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerSecondRequest.json")))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(responses), Some(name), ListOptions(resourceVersion= Some("12802"),timeoutSeconds = Some(1)), 10000)
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

      verify(client, times(4)).logConfig
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(query1), null
      )
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(query2), null
      )

      ok
    }

    "read event continuously with name specified from the beginning" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))
      val secondRequest = HttpRequest(uri = Uri("http://watch/2"))
      val name="someName"
      val nameFieldSelector=s"metadata.name=$name"
      val query1=Uri.Query("timeoutSeconds" -> "1", "watch" -> "true", "fieldSelector" -> nameFieldSelector)
      val query2=Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804", "watch" -> "true","fieldSelector" -> nameFieldSelector)
      when(client.logConfig).thenReturn(LoggingConfig())
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(query1), null)
      ).thenReturn(firstRequest)
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(query2), null)
      ).thenReturn(secondRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerFirstRequest.json"))),
        secondRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerSecondRequest.json")))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(responses), Some(name), ListOptions(timeoutSeconds = Some(1)), 10000)
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

      verify(client, times(4)).logConfig
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(query1), null
      )
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(query2), null
      )

      ok
    }

    "handle empty responses from the cluster when request timeout times out" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))
      val secondRequest = HttpRequest(uri = Uri("http://watch/2"))
      val thirdRequest = HttpRequest(uri = Uri("http://watch/3"))

      when(client.logConfig).thenReturn(LoggingConfig())
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null)
      ).thenReturn(firstRequest)
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804", "watch" -> "true")), null)
      ).thenReturn(secondRequest).thenReturn(thirdRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerFirstRequest.json"))),
        secondRequest -> HttpResponse(StatusCodes.OK, entity = ""),
        thirdRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity(retrieveWatchJson("/watchReplicationControllerSecondRequest.json")))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(responses), None, ListOptions(resourceVersion=Some("12802"), timeoutSeconds=Some(1)), 10000)
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

      verify(client, times(6)).logConfig
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null
      )
      verify(client, times(2)).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12804", "watch" -> "true")), null
      )
      ok
    }

    "handle bad input from cluster" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(client.logConfig).thenReturn(LoggingConfig())
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null)
      ).thenReturn(firstRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = "bad input")
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(responses), None, ListOptions(resourceVersion=Some("12802"), timeoutSeconds=Some(1)), 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[FramingException]
      error.getMessage mustEqual "Invalid JSON encountered at position [0] of [ByteString(98, 97, 100, 32, 105, 110, 112, 117, 116)]"

      verify(client, times(2)).logConfig
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null
      )

      ok
    }

    "handle bad json from cluster" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(client.logConfig).thenReturn(LoggingConfig())
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null)
      ).thenReturn(firstRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.OK, entity = createHttpEntity("{asdf:asdfa}"))
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(responses), None, ListOptions(resourceVersion=Some("12802"), timeoutSeconds=Some(1)), 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[JsonParseException]

      verify(client, times(2)).logConfig
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null
      )

      ok
    }

    "handle a HTTP 500 error from service" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(client.logConfig).thenReturn(LoggingConfig())
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null)
      ).thenReturn(firstRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.InternalServerError)
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(responses), None, ListOptions(resourceVersion=Some("12802"), timeoutSeconds=Some(1)), 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[K8SException]
      error.asInstanceOf[K8SException].status.code mustEqual Some(500)

      verify(client).logConfig
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null
      )

      ok
    }

    "handle a HTTP 401 error from service" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(client.logConfig).thenReturn(LoggingConfig())
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null)
      ).thenReturn(firstRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.Unauthorized)
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(responses), None, ListOptions(resourceVersion=Some("12802"), timeoutSeconds=Some(1)), 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[K8SException]
      error.asInstanceOf[K8SException].status.code mustEqual Some(401)

      verify(client).logConfig

      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null
      )

      ok
    }

    "handle idle timeout from service" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(client.logConfig).thenReturn(LoggingConfig())
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null)
      ).thenReturn(firstRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.InternalServerError)
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(new TcpIdleTimeoutException("timeout", 10.seconds)), None, ListOptions(resourceVersion=Some("12802"), timeoutSeconds=Some(1)), 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[K8SException]

      verify(client).logConfig

      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null
      )

      ok
    }

    "handle connection timeout from service" >> {
      val client = mock[KubernetesClientImpl]
      val firstRequest = HttpRequest(uri = Uri("http://watch/1"))

      when(client.logConfig).thenReturn(LoggingConfig())
      when(client.buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null)
      ).thenReturn(firstRequest)

      val responses = Map(
        firstRequest -> HttpResponse(StatusCodes.InternalServerError)
      )

      val (switch, downstream) =
        WatchSource[ReplicationController](client, mockPool(new ConnectException(s"Connect timeout of 10s expired")), None, ListOptions(resourceVersion=Some("12802"), timeoutSeconds=Some(1)), 10000)
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(TestSink.probe)(Keep.both)
          .run()

      val error = downstream
        .request(1)
        .expectError()

      error must haveClass[K8SException]

      verify(client).logConfig
      verify(client).buildRequest(
        HttpMethods.GET, skuber.ReplicationController.rcDef, None, Some(Uri.Query("timeoutSeconds" -> "1", "resourceVersion" -> "12802", "watch" -> "true")), null
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
                      imagePullPolicy = Some(Container.PullPolicy.IfNotPresent)
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
