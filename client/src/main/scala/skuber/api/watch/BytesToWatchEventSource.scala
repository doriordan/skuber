package skuber.api.watch

import org.apache.pekko.stream.scaladsl.{JsonFraming, Source}
import org.apache.pekko.util.ByteString
import play.api.libs.json.{Format, JsError, JsSuccess, Json}
import skuber.ObjectResource
import skuber.api.client.{K8SException, Status, WatchEvent}

import scala.concurrent.ExecutionContext

/**
  * Convert a source of bytes to a source of watch events of type O
  */
private[api] object BytesToWatchEventSource {
  def apply[O <: ObjectResource](bytesSource: Source[ByteString, _], bufSize: Int)(implicit ec: ExecutionContext, format: Format[O]): Source[WatchEvent[O], _] = {
    import skuber.json.format.apiobj.watchEventFormat
    bytesSource.via(
      JsonFraming.objectScanner(bufSize)
    ).map { singleEventBytes =>
      Json.parse(singleEventBytes.utf8String).validate(watchEventFormat[O]) match {
        case JsSuccess(value, _) => value
        case JsError(e) => throw new K8SException(Status(message = Some("Error parsing watched object"), details = Some(e.toString)))
      }
    }
  }
}
