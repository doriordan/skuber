package skuber.api.watch

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.Materializer
import skuber.api.client.Pool

import scala.concurrent.duration._

private[api] object LongPollingPool {
  def apply[T](schema: String, host: String, port: Int,
               poolIdleTimeout: Duration,
               httpsConnectionContext: Option[HttpsConnectionContext],
               clientConnectionSettings: ClientConnectionSettings)(implicit system: ActorSystem): Pool[T] = {
    schema match {
      case "http" =>
        Http().newHostConnectionPool[T](host, port,
          buildHostConnectionPool(poolIdleTimeout, clientConnectionSettings, system)).mapMaterializedValue(_ => NotUsed)
      case "https" =>
        Http().newHostConnectionPoolHttps[T](host, port,
          httpsConnectionContext.getOrElse(Http().defaultClientHttpsContext),
          buildHostConnectionPool(poolIdleTimeout, clientConnectionSettings, system)).mapMaterializedValue(_ => NotUsed)
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
