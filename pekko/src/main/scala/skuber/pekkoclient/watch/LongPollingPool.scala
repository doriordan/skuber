package skuber.pekkoclient.watch

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import org.apache.pekko.http.scaladsl.{Http, HttpsConnectionContext}
import skuber.pekkoclient.Pool

import scala.concurrent.duration._

private[pekkoclient] object LongPollingPool {
  def apply[T](schema: String, host: String, port: Int,
               poolIdleTimeout: Duration,
               httpsConnectionContext: Option[HttpsConnectionContext],
               clientConnectionSettings: ClientConnectionSettings)(implicit system: ActorSystem): Pool[T] = {
    schema match {
      case "http" =>
        Http().newHostConnectionPool[T](
          host, port,
          buildHostConnectionPool(poolIdleTimeout, clientConnectionSettings, system)
        ).mapMaterializedValue(_ => NotUsed)
      case "https" =>
        Http().newHostConnectionPoolHttps[T](
          host, port,
          httpsConnectionContext.getOrElse(Http().defaultClientHttpsContext),
          buildHostConnectionPool(poolIdleTimeout, clientConnectionSettings, system)
        ).mapMaterializedValue(_ => NotUsed)
      case unsupported =>
        throw new IllegalArgumentException(s"Schema $unsupported is not supported")
    }
  }

  private def buildHostConnectionPool(poolIdleTimeout: Duration, clientConnectionSettings: ClientConnectionSettings, system: ActorSystem) = {
    ConnectionPoolSettings(system)
      .withMaxConnections(1)              // Limit number the of open connections to one
      .withPipeliningLimit(1)             // Limit pipelining of requests to one
      .withMaxRetries(0)                  // Disables retries
      .withIdleTimeout(poolIdleTimeout)   // Automatically shutdown connection pool after timeout
      .withConnectionSettings(clientConnectionSettings)
  }
}
