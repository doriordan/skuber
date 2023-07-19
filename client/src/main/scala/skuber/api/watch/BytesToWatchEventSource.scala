package skuber.api.watch

import akka.NotUsed
import akka.stream.scaladsl.{JsonFraming, Sink, Source}
import akka.util.ByteString
import play.api.libs.json.{Format, JsError, JsObject, JsString, JsSuccess, JsValue, Json}
import skuber.model.ObjectResource
import skuber.api.client.impl.KubernetesClientImpl
import skuber.api.client.{K8SException, LoggingContext, Status, WatchEvent}

import scala.concurrent.ExecutionContext

/**
  * Convert a source of bytes to a source of watch events of type O
  */
private[api] object BytesToWatchEventSource {
  def apply[O <: ObjectResource](client: KubernetesClientImpl, bytesSource: Source[ByteString, _], bufSize: Int, errorHandlerOpt: Option[String => _] = None)(implicit ec: ExecutionContext, format: Format[O], lc: LoggingContext): Source[WatchEvent[O], _] = {
    import skuber.json.format.apiobj.watchEventFormat
    bytesSource.via(
      JsonFraming.objectScanner(bufSize)
    ).map { singleEventBytes =>
      Json.parse(singleEventBytes.utf8String).as[JsObject]
    }.filter {
      case JsObject(underlying) if underlying.get("type").contains(JsString("ERROR")) =>
        // handle error events, removing them from downstream emitted elements
        val handled = for {
          errorHandler <- errorHandlerOpt
          errorObj <- underlying.get("object").flatMap(_.asOpt[JsObject])
        } yield {
          errorHandler(errorObj.toString)
        }
        if (!handled.isDefined) {
          // no error handler, just log instead
          client.logWarn(s"Watcher received ERROR event: $underlying")
        }
        false
      case _ => true
    }.map { eventJson =>
      eventJson.validate(watchEventFormat[O]) match {
        case JsSuccess(value, _) => value
        case JsError(e) => throw new K8SException(Status(message = Some("Error parsing watched object"), details = Some(e.toString)))
      }
    }
  }
}
