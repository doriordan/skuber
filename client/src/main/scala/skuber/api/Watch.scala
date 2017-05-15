package skuber.api


import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import play.api.libs.iteratee.streams.IterateeStreams
import skuber.ObjectResource
import skuber.json.format.apiobj.watchEventFormat
import skuber.api.client.{K8SException, ObjKind, RequestContext, Status, WatchEvent}

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.StreamedResponse
import play.api.libs.json.{Format, JsError, JsResult, JsSuccess}
import play.api.libs.iteratee.{Enumeratee, Enumerator}
import play.extras.iteratees.{Encoding, JsonIteratees}

import scala.concurrent.duration._

import org.slf4j.LoggerFactory

import scala.language.postfixOps

/**
 * @author David O'Riordan
 * Handling of the Json event objects streamed in response to a Kubernetes API watch request
 * Based on Play iteratee library + Play extras json iteratees
 */
object Watch {

    val log = LoggerFactory.getLogger("skuber.api")
    def pulseEvent = """{ "pulseEvent" : "" }""".getBytes
    
    def events[O <: ObjectResource](
        context: RequestContext, 
        name: String,
        sinceResourceVersion: Option[String] = None)
        (implicit format: Format[O], kind: ObjKind[O], ec: ExecutionContext, system: ActorSystem) : Watch[WatchEvent[O]] =
    {
      implicit val materializer = ActorMaterializer()
       if (log.isDebugEnabled) 
         log.debug("[Skuber Watch (" + name + "): creating...")
       val wsReq = context.buildRequest(Some(name), watch=true)(kind).
                               withRequestTimeout(Duration.Inf)
       val maybeResourceVersionParam = sinceResourceVersion map { "resourceVersion" -> _ }
       val watchRequest = maybeResourceVersionParam map { wsReq.withQueryString(_) } getOrElse wsReq

      val futureResponse: Future[StreamedResponse] = watchRequest.stream()

      val responseBytesEnumerator = IterateeStreams.publisherToEnumerator(IterateeStreams.futureToPublisher(futureResponse.flatMap { res =>
        res.body.runWith(Sink.fold[Array[Byte], ByteString](Array[Byte]()) { (_, bytes) =>
          bytes.toArray
        })
      }))
       
       eventsEnumerator(name, responseBytesEnumerator)  
    }
    
    def eventsOnKind[O <: ObjectResource](
        context: RequestContext, 
        sinceResourceVersion: Option[String] = None)
        (implicit format: Format[O], kind: ObjKind[O], ec: ExecutionContext, system: ActorSystem) : Watch[WatchEvent[O]] =
    {
      implicit val materializer = ActorMaterializer()
        val watchId = "/" + kind.urlPathComponent
        if (log.isDebugEnabled) 
          log.debug("[Skuber Watch (" + watchId + ") : creating...")
        val wsReq = context.buildRequest(None, watch=true)(kind).
                              withRequestTimeout(Duration.Inf)
                                 
        val maybeResourceVersionParam = sinceResourceVersion map { "resourceVersion" -> _ }
        val watchRequest = maybeResourceVersionParam map { wsReq.withQueryString(_) } getOrElse(wsReq)
      val futureResponse: Future[StreamedResponse] = watchRequest.stream()


      val responseBytesEnumerator = IterateeStreams.publisherToEnumerator(IterateeStreams.futureToPublisher(futureResponse.flatMap { res =>
        res.body.runWith(Sink.fold[Array[Byte], ByteString](Array[Byte]()) { (_, bytes) =>
          bytes.toArray
        })
      }))
       
        eventsEnumerator(watchId, responseBytesEnumerator)  
          
    }
    
    def eventsEnumerator[O <: ObjectResource](
        watchId: String, // for logging only
        bytes : Enumerator[Array[Byte]])
        (implicit format: Format[O], ec: ExecutionContext) : Watch[WatchEvent[O]] = 
    {
      // interleave a regular pulse: workaround for apparent issue that last event in Watch response 
      // stream doesn't get enumerated until another event is received: problematic when you want 
      // to react to that last event but  don't expect more events imminently (Guestbook is an example)
      // The pulse events will be filtered out by an enumeratee in fromBytesEnumerator.
      val pulseWatch = pulse
      val bytesWithPulse = bytes interleave pulseWatch.events
      val enumerator = fromBytesEnumerator(watchId, bytesWithPulse)
      Watch(enumerator, pulseWatch.terminate)
    }
    
    def fromBytesEnumerator[O <: ObjectResource](
        watchId: String,
        bytes : Enumerator[Array[Byte]])
        (implicit format: Format[O], ec: ExecutionContext) : Enumerator[WatchEvent[O]] = 
    {
      bytes &>
         Encoding.decode() &>
         Enumeratee.grouped(JsonIteratees.jsSimpleObject) ><>
         Enumeratee.filter { jsObject => !jsObject.keys.contains("pulseEvent") } &>
         Enumeratee.map { watchEventFormat[O].reads } &>
         Enumeratee.collect[JsResult[WatchEvent[O]]] {
           case JsSuccess(value, _) => {
             if (log.isDebugEnabled)
               log.debug("[Skuber Watch (" + watchId + "): successfully parsed watched object : " + value + "]") 
               value
           } 
           case JsError(e) => {
             log.error("[Skuber Watch (" + watchId + "): Json parsing error - " + e + "]")
             throw new K8SException(Status(message=Some("Error parsing watched object"), details=Some(e.toString)))
           }                      
         }
    }

    def events[O <: ObjectResource](
        k8sContext: RequestContext,
        obj: O)
        (implicit format: Format[O], kind: ObjKind[O],ec: ExecutionContext, system: ActorSystem) : Watch[WatchEvent[O]] =
    {
      events(k8sContext, obj.name, Option(obj.metadata.resourceVersion).filter(_.trim.nonEmpty))  
    }
    
    
    
    def pulse()(implicit ec: ExecutionContext) : Watch[Array[Byte]] = {
      var terminated = false
      def isTerminated = terminated  
      val pulseEvents = Enumerator.generateM { 
        if (isTerminated) 
           Future { None }
        else {
          Future { Thread.sleep(100); Some(pulseEvent) }
        }
      }
      val terminate = () => { terminated=true }
      Watch(pulseEvents, terminate)
    }    
}

case class Watch[O](events: Enumerator[O], terminate: () => Unit)
