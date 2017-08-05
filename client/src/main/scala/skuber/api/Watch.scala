package skuber.api


import skuber.{ResourceDefinition,ObjectResource}
import skuber.json.format.apiobj.watchEventFormat
import skuber.api.client.{RequestContext,K8SException, WatchEvent, Status}

import scala.concurrent.{Future,ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.ws.WSRequest
import play.api.libs.ws.WSClientConfig

import play.api.libs.json.{JsSuccess, JsError, JsObject, JsValue, JsResult, Format}
import play.api.libs.iteratee.{Concurrent, Iteratee, Enumerator, Enumeratee, Input, Done, Cont}
import play.extras.iteratees.{CharString, JsonEnumeratees, JsonIteratees, JsonParser, Encoding}
import play.api.libs.concurrent.Promise

import scala.concurrent.duration._

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.language.postfixOps
import play.api.libs.ws.ning._

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
        (implicit format: Format[O], rd: ResourceDefinition[O], ec: ExecutionContext) : Watch[WatchEvent[O]] =
    {
       if (log.isDebugEnabled) 
         log.debug("[Skuber Watch (" + name + "): creating...")
       val wsReq = context.buildRequest(rd, Some(name), watch=true).withRequestTimeout(2147483647)
       val maybeResourceVersionParam = sinceResourceVersion map { "resourceVersion" -> _ }
       val watchRequest = maybeResourceVersionParam map { wsReq.withQueryString(_) } getOrElse(wsReq)
       val (responseBytesIteratee, responseBytesEnumerator) = Concurrent.joined[Array[Byte]]
       
       watchRequest.get(_ => responseBytesIteratee).flatMap(_.run)
       
       eventsEnumerator(name, responseBytesEnumerator)  
    }
    
    def eventsOnKind[O <: ObjectResource](
        context: RequestContext, 
        sinceResourceVersion: Option[String] = None)
        (implicit format: Format[O], rd: ResourceDefinition[O], ec: ExecutionContext) : Watch[WatchEvent[O]] =
    {
        val watchId = "/" + rd.spec.names.plural
        if (log.isDebugEnabled) 
          log.debug("[Skuber Watch (" + watchId + ") : creating...")
        val wsReq = context.buildRequest(rd, None, watch=true).withRequestTimeout(2147483647)
                                 
        val maybeResourceVersionParam = sinceResourceVersion map { "resourceVersion" -> _ }
        val watchRequest = maybeResourceVersionParam map { wsReq.withQueryString(_) } getOrElse(wsReq)
        val (responseBytesIteratee, responseBytesEnumerator) = Concurrent.joined[Array[Byte]]
       
        watchRequest.get(_ => responseBytesIteratee).flatMap(_.run)
       
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
        (implicit format: Format[O], rd: ResourceDefinition[O],ec: ExecutionContext) : Watch[WatchEvent[O]] =
    {
      events(k8sContext, obj.name, Option(obj.metadata.resourceVersion).filter(_.trim.nonEmpty))  
    }
    
    
    
    def pulse : Watch[Array[Byte]] = {
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
