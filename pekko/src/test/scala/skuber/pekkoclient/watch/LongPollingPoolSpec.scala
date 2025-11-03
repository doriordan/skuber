package skuber.pekkoclient.watch

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.settings.ClientConnectionSettings
import org.apache.pekko.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import com.typesafe.sslconfig.pekko.PekkoSSLConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.specs2.mutable.Specification

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Success

class LongPollingPoolSpec extends Specification with ScalaFutures {
  implicit val system: ActorSystem = ActorSystem("watch-source-spec")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(5, Millis))

  val route: Route = get {
    path("ping") {
      complete("pong")
    }
  }

  "LongPollingPool" should {
    "create a http pool" >> {
      val clientConfig = ClientConnectionSettings(system.settings.config)
      val bindingFuture = Http().bindAndHandle(Route.handlerFlow(route), "127.0.0.1", port = 4321)

      val pool = LongPollingPool[Int]("http", "localhost", 4321, 30.seconds, None, clientConfig)

      val result = Source.single(HttpRequest(HttpMethods.GET, Uri("http://localhost:4321/ping")))
        .map(x => x -> 1).via(pool)
        .map(_._1).runWith(Sink.head)
        .futureValue

      result.map(_.status) must beEqualTo(Success(StatusCodes.OK))

      bindingFuture.flatMap(_.unbind()).futureValue must beEqualTo(Done)
    }

    "create a https pool" >> {
      val clientConfig = ClientConnectionSettings(system.settings.config)

      val password: Array[Char] = "abcdef".toCharArray

      val ks: KeyStore = KeyStore.getInstance("PKCS12")

      val keystore: InputStream = getClass.getClassLoader.getResourceAsStream("key/test-server-cert.p12")

      require(keystore != null, "Keystore required!")
      ks.load(keystore, password)

      val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
      keyManagerFactory.init(ks, password)

      val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
      tmf.init(ks)

      val sslContext: SSLContext = SSLContext.getInstance("TLS")
      sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)

      val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
      val bindingFuture = Http().bindAndHandle(Route.handlerFlow(route), "127.0.0.1", port = 4322, connectionContext = https)

      val clientHttps: HttpsConnectionContext = new HttpsConnectionContext(sslContext, Some(
        PekkoSSLConfig().mapSettings(x => x.withLoose {
          x.loose.withDisableHostnameVerification(true)
        })
      ))

      val pool = LongPollingPool[Int]("https", "localhost", 4322, 30.seconds, Some(clientHttps), clientConfig)

      val result = Source.single(HttpRequest(HttpMethods.GET, Uri("http://localhost:4322/ping")))
        .map(x => x -> 1).via(pool)
        .map(_._1).runWith(Sink.head)
        .futureValue

      result.map(_.status) must beEqualTo(Success(StatusCodes.OK))

      bindingFuture.flatMap(_.unbind()).futureValue must beEqualTo(Done)
    }

    "handle unsupported scheme" >> {
      LongPollingPool[Int](
        "badScheam",
        "localhost", 4322, 30.seconds, None,
        ClientConnectionSettings(system.settings.config)
      ) must throwA[IllegalArgumentException]
    }

  }
}
