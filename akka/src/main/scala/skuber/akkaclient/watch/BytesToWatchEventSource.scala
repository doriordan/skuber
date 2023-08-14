package skuber.akkaclient.watch

import akka.stream.scaladsl.{JsonFraming, Source}
import akka.util.ByteString
import play.api.libs.json._
import skuber.akkaclient.impl.AkkaKubernetesClientImpl
import skuber.api.client.{K8SException, LoggingContext, Status, WatchEvent}
import skuber.model.ObjectResource

import scala.concurrent.ExecutionContext

/**
  * Convert a source of bytes to a source of watch events of type O
  */
private[akkaclient] object BytesToWatchEventSource {
  def apply[O <: ObjectResource](client: AkkaKubernetesClientImpl, bytesSource: Source[ByteString, _], bufSize: Int, errorHandlerOpt: Option[String => _] = None)(implicit ec: ExecutionContext, format: Format[O], lc: LoggingContext): Source[WatchEvent[O], _] = {
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
