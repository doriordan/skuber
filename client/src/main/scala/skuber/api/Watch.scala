package skuber.api

import skuber.model.coretypes.ObjectResource
import skuber.json.format.apiobj.watchEventFormat
import skuber.api.client.{K8SRequestContext,K8SException, WatchEvent, ObjKind, Status}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.ws.WSRequest

import play.api.libs.json.{JsSuccess, JsError, JsObject, JsValue, JsResult, Format}
import play.api.libs.iteratee.{Concurrent, Iteratee, Enumerator, Enumeratee, Input, Done, Cont}
import play.extras.iteratees.{CharString, JsonEnumeratees, JsonIteratees, JsonParser, Encoding}
import play.api.libs.concurrent.Promise

import scala.concurrent.duration._

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.language.postfixOps

/**
 * @author David O'Riordan
 * Handling of the Json event objects streamed in response to a Kubernetes API watch request
 * Based on Play iteratee library + Play extras json iteratees
 */
object Watch {
  
    val log = LoggerFactory.getLogger("skuber.api")
    
    def pulseEvent = """{pulseEvent : ""}""".getBytes
    
    def events[O <: ObjectResource](
        k8sContext: K8SRequestContext, 
        name: String,
        sinceResourceVersion: Option[String] = None)
        (implicit format: Format[O], kind: ObjKind[O], ec: ExecutionContext) : Enumerator[WatchEvent[O]] = 
    {
       if (log.isDebugEnabled) 
         log.debug("[Skuber Watch (" + name + "): creating...")
       val wsReq = k8sContext.buildRequest(Some(kind.urlPathComponent), Some(name), watch=true).withRequestTimeout(2147483647)
       val maybeResourceVersionParam = sinceResourceVersion map { "resourceVersion" -> _ }
       val watchRequest = maybeResourceVersionParam map { wsReq.withQueryString(_) } getOrElse(wsReq)
       val (responseBytesIteratee, responseBytesEnumerator) = Concurrent.joined[Array[Byte]]
       
       watchRequest.get(_ => responseBytesIteratee).flatMap(_.run)
       
       fromBytesEnumerator(name, responseBytesEnumerator)  
    }
    
    def fromBytesEnumerator[O <: ObjectResource](
        watchId: String,
        bytes : Enumerator[Array[Byte]])
        (implicit format: Format[O], kind: ObjKind[O], ec: ExecutionContext) : Enumerator[WatchEvent[O]] = 
    {
      // interleaving a pulse is a workaround for apparent issue whereby last event is not emitted by grouped enumeratee until another event 
      // is received (note we never get an EOF with this stream). The pulse is filtered out of the returned enumerator. TBD confirm / resolve 
      // underlying issue
      val bytesWithPulse = pulse 
      pulse &>
         Enumeratee.map { x =>  
           // purely for debugging
           if (log.isDebugEnabled) 
             log.debug("[Skuber Watch (" + watchId + "): handling chunk HEX = " + toHex(x) + "]")
           x  
         } &>
         Encoding.decode() ><>
         Enumeratee.map { x =>  
           // purely for debugging
           if (log.isDebugEnabled) 
             log.debug("[Skuber Watch (" + watchId + "): handling chunk :'" + x.mkString + "']")
           x
         } ><>
//         Enumeratee.grouped(JsonIteratees.jsSimpleObject) ><>
         Enumeratee.grouped(WatchResponseJsonParser.jsonObject) ><>
         Enumeratee.filter { x =>
           if (log.isDebugEnabled) 
             log.debug("[Skuber Watch (" + watchId + "): handling jsObject ='" + x.toString + "'")
           x.keys.contains("pulseEvent")  
         } ><>
         Enumeratee.map { watchEventFormat[O].reads } ><>
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
    
    private def toHex(bytes: Array[Byte]): String =
    {
      val buffer = new StringBuilder(bytes.length * 2)
      for(i <- 0 until bytes.length)
      {
        val b = bytes(i)
        val bi: Int = if(b < 0) b + 256 else b
        buffer append toHex((bi >>> 4).asInstanceOf[Byte])
        buffer append toHex((bi & 0x0F).asInstanceOf[Byte])
      }
      buffer.toString
    }
    
    private def toHex(b: Byte): Char =
    {
      require(b >= 0 && b <= 15, "Byte " + b + " was not between 0 and 15")
      if(b < 10)
        ('0'.asInstanceOf[Int] + b).asInstanceOf[Char]
      else
        ('a'.asInstanceOf[Int] + (b-10)).asInstanceOf[Char]
    }
    
    def events[O <: ObjectResource](
        k8sContext: K8SRequestContext,
        obj: O)
        (implicit format: Format[O], kind: ObjKind[O],ec: ExecutionContext) : Enumerator[WatchEvent[O]] =
    {
      events(k8sContext, obj.name, Option(obj.metadata.resourceVersion).filter(_.trim.nonEmpty))  
    }
    
    private def pulse: Enumerator[Array[Byte]] = Enumerator.generateM(Promise.timeout(Some(pulseEvent), 100 milliseconds))
}

object WatchResponseJsonEnumeratees {
  
    def jsObjects(watchId : String): Enumeratee[CharString, JsObject] =  jsObjects(watchId, WatchResponseJsonParser.jsonObject)
           
    def jsObjects(watchId: String, jsonObjectParser: Iteratee[CharString, JsObject]) = new Enumeratee[CharString, JsObject] {
      def step[A](inner: Iteratee[JsObject, A])(in: Input[JsObject]): Iteratee[JsObject, Iteratee[JsObject, A]] = in match {
        case Input.EOF => {
          if (Watch.log.isDebugEnabled) 
              Watch.log.debug("[Skuber Watch (" + watchId + ") : handling EOF")
           Done(inner, in)
        }
        case _ => {
          if (Watch.log.isDebugEnabled) 
              Watch.log.debug("[Skuber Watch (" + watchId + ") : handling input " + in)
          Cont(step(Iteratee.flatten(inner.feed(in))))
        }
      }

      def applyOn[A](inner: Iteratee[JsObject, A]): Iteratee[CharString, Iteratee[JsObject, A]] = {
        if (Watch.log.isDebugEnabled) 
              Watch.log.debug("[Skuber Watch (" + watchId + ") : applyOn called")
        WatchResponseJsonParser.jsonObjects(watchId, Cont(step(inner)), jsonObjectParser)
      }
    }
}

object WatchResponseJsonParser {
  
    import play.extras.iteratees.Combinators._
    
    def jsonObjects[A](watchId: String,
                       jsonObjectHandler: Iteratee[JsObject, A],
                       jsonObjectParser: Iteratee[CharString, JsObject]): Iteratee[CharString, A] =
                    
    for {
      _ <- skipWhitespace
      log1 = if (Watch.log.isDebugEnabled) 
                Watch.log.debug("[Skuber Watch (" + watchId + ") : jsonObjects: about to parse next object")
      fed <- jsonObjectParser.map(jsObj => Iteratee.flatten(jsonObjectHandler.feed(Input.El(jsObj))))
      log2 = if (Watch.log.isDebugEnabled)
                Watch.log.debug("[Skuber Watch (" + watchId + ") : jsonObjects: object fed")
      values <- jsonObjects(watchId, fed, jsonObjectParser)
    } yield values
    
    def jsonObject: Iteratee[CharString, JsObject] = jsonObject()

    def jsonObject[A, V](keyValuesHandler: Iteratee[V, A] = JsonParser.jsonObjectCreator,
                         valueHandler: String => Iteratee[CharString, V] = (key: String) => JsonParser.jsonValue.map(value => (key, value))
                        ): Iteratee[CharString, A] =
    jsonObjectImpl(keyValuesHandler, valueHandler)

   private def jsonObjectImpl[A, V](keyValuesHandler: Iteratee[V, A],
                       valueHandler: String => Iteratee[CharString, V]) = for {
     _ <- skipWhitespace
     _ <- expect('{')
     _ <- skipWhitespace
     ch <- peekOne
     keyValues <- ch match {
       case Some('}') => drop(1).flatMap(_ => Iteratee.flatten(keyValuesHandler.run.map((a: A) => done(a))))
       case _ => {
         if (Watch.log.isDebugEnabled) 
                Watch.log.debug("[Skuber Watch: iteratee - in json object parser: parsing key/values...")
         JsonParser.jsonKeyValues(keyValuesHandler, valueHandler)
       }
     }
     _ <- skipWhitespace 
    } yield keyValues
}