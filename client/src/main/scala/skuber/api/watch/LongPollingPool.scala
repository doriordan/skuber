package skuber.api.watch

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import skuber.api.client.Pool

import scala.concurrent.duration._
import scala.util.Success

private[api] object LongPollingPool {
  def apply[T](schema: String, host: String, port: Int,
               poolIdleTimeout: Duration,
               httpsConnectionContext: Option[HttpsConnectionContext],
               clientConnectionSettings: ClientConnectionSettings)(implicit system: ActorSystem): Pool[T] = {
    implicit val ec = system.dispatcher
    schema match {
      case "http" | "https" =>
        Flow[(HttpRequest, T)]
          .mapAsync(1) {
            case (request, userdata) =>
              Http()
                .singleRequest(
                  request,
                  httpsConnectionContext.getOrElse(Http().defaultClientHttpsContext),
                  buildHostConnectionPool(poolIdleTimeout, clientConnectionSettings, system)
                )
                .map(response => (Success(response), userdata))
          }
      case unsupported =>
        throw new IllegalArgumentException(s"Schema $unsupported is not supported")
    }
  }

  private def buildHostConnectionPool[T](poolIdleTimeout: Duration, clientConnectionSettings: ClientConnectionSettings, system: ActorSystem) = {
    ConnectionPoolSettings(system)
      .withMaxConnections(1)              // Limit number the of open connections to one
      .withPipeliningLimit(1)             // Limit pipelining of requests to one
      .withMaxRetries(0)                  // Disables retries
      .withIdleTimeout(poolIdleTimeout)   // Automatically shutdown connection pool after timeout
      .withConnectionSettings(clientConnectionSettings)
  }
}
