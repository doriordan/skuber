package skuber.mock

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import io.fabric8.kubernetes.client.server.mock.{KubernetesCrudDispatcher, KubernetesMockServer}
import io.fabric8.mockwebserver.{ServerRequest, ServerResponse}
import okhttp3.mockwebserver.{MockResponse, MockWebServer}
import org.scalatest.{FutureOutcome, fixture}
import skuber.api.client._

trait MockK8SFixture extends fixture.AsyncFlatSpec {

  override type FixtureParam = RequestContext

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  class KubernetesCrudDispatcherWithContentType extends KubernetesCrudDispatcher {
    override def handleGet(path: String): MockResponse = {
      val response = super.handleGet(path)

      response.setHeader("Content-Type", "application/json")
    }

    override def handleCreate(path: String, request: String): MockResponse = {
      val response = super.handleCreate(path, request)

      response.setHeader("Content-Type", "application/json")
    }
  }

  val server = new KubernetesMockServer(
    new io.fabric8.mockwebserver.Context(),
    new MockWebServer(),
    new java.util.HashMap[ServerRequest, java.util.Queue[ServerResponse]](),
    new KubernetesCrudDispatcherWithContentType,
    true)
  server.init()

  val cluster = Cluster(server = s"https://${server.getHostName}:${server.getPort}", insecureSkipTLSVerify = true)
  val context = Context(cluster = cluster)
  val configuration = skuber.api.Configuration(
    clusters = Map("default" -> cluster),
    contexts = Map("default" -> context),
    currentContext = context
  )

  override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val k8s = skuber.k8sInit(configuration)
    complete {
      withFixture(test.toNoArgAsyncTest(k8s))
    } lastly {
      k8s.close
    }
  }
}
