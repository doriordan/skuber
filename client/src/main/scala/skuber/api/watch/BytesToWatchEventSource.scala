package skuber.api.watch

import org.apache.pekko.stream.scaladsl.{JsonFraming, Source}
import org.apache.pekko.util.ByteString
import play.api.libs.json.{Format, JsError, JsString, JsSuccess, Json}
import skuber.ObjectResource
import skuber.api.client.{K8SException, Status, WatchEvent}
import scala.concurrent.ExecutionContext

/**
  * Convert a source of bytes to a source of watch events of type O
  */
private[api] object BytesToWatchEventSource {
  def apply[O <: ObjectResource](bytesSource: Source[ByteString, _], bufSize: Int)(implicit ec: ExecutionContext, format: Format[O]): Source[WatchEvent[O], _] = {
    import skuber.json.format.apiobj.watchEventWrapperReads
    bytesSource.via(
      JsonFraming.objectScanner(bufSize)
    ).map { singleEventBytes =>
      Json.parse(singleEventBytes.utf8String).validate(watchEventWrapperReads[O]) match {
        case JsSuccess(value, _) => value match {
          case Left(status) => throw new K8SException(status)
          case Right(watchEvent) => watchEvent
        }
        case JsError(e) =>
          val details = s"error: $e event: ${singleEventBytes.utf8String}"
          throw new K8SException(Status(message = Some("Error parsing watched object"), details = Some(JsString(details))))
      }
    }
  }
}
