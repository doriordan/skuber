package skuber.api.watch

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.scaladsl.{Flow, Sink, Source}
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
      case "http" =>
        Flow[(HttpRequest, T)]
          .mapAsync(1) {
            case (request, userdata) =>
              Source.single(request)
                .via(Http().outgoingConnection(host, port, settings = clientConnectionSettings))
                .map(response => (Success(response), userdata))
                .runWith(Sink.head)
          }
      case "https" =>
        Flow[(HttpRequest, T)]
          .mapAsync(1) {
            case (request, userdata) =>
              Source.single(request)
                .via(Http().outgoingConnectionHttps(host, port, httpsConnectionContext.get, settings = clientConnectionSettings))
                .map(response => (Success(response), userdata))
                .runWith(Sink.head)
          }
      case unsupported =>
        throw new IllegalArgumentException(s"Schema $unsupported is not supported")
    }
  }
}
