package skuber.api

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.settings.ClientConnectionSettings
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.specs2.mutable.Specification
import skuber.api.watch.LongPollingPool

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
      val bindingFuture = Http().newServerAt( "127.0.0.1", port = 4321).bind(route)

      val pool = LongPollingPool[Int]("http", "localhost", 4321, 30.seconds, None, clientConfig)

      val result = Source.single(HttpRequest(HttpMethods.GET, Uri("http://localhost:4321/ping")))
        .map(x => x -> 1).via(pool)
        .map(_._1).runWith(Sink.head)
        .futureValue

      result.map(_.status) mustEqual Success(StatusCodes.OK)

      bindingFuture.flatMap(_.unbind()).futureValue mustEqual Done
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

      // create a client side SSL engine with hostname verification disabled ( purely for local testing purposes)
      def createClientSSLEngine(host: String, port: Int) = {
        val sslEngine = sslContext.createSSLEngine()
        sslEngine.setUseClientMode(true)
        sslEngine
      }
      val clientHttps: HttpsConnectionContext = ConnectionContext.httpsClient(createClientSSLEngine _)
      val serverHttps: HttpsConnectionContext = ConnectionContext.httpsServer(sslContext)
      val bindingFuture = Http().newServerAt("127.0.0.1", port = 4322).enableHttps(serverHttps).bind(route)

      val pool = LongPollingPool[Int]("https", "localhost", 4322, 30.seconds, Some(clientHttps), clientConfig)

      val result = Source.single(HttpRequest(HttpMethods.GET, Uri("http://localhost:4321/ping")))
        .map(x => x -> 1).via(pool)
        .map(_._1).runWith(Sink.head)
        .futureValue

      result.map(_.status) mustEqual Success(StatusCodes.OK)

      bindingFuture.flatMap(_.unbind()).futureValue mustEqual Done
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
