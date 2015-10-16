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

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author David O'Riordan
 * Handling of the Json event objects streamed in response to a Kubernetes API watch request
 * Based on Play iteratee library + Play extras json iteratees
 */
object Watch {
  
    val log = LoggerFactory.getLogger("skuber.api")
                        
    def events[O <: ObjectResource](
        k8sContext: K8SRequestContext, 
        name: String,
        sinceResourceVersion: Option[String] = None)
        (implicit format: Format[O], kind: ObjKind[O], ec: ExecutionContext) : Enumerator[WatchEvent[O]] = 
    {
       if (log.isDebugEnabled) 
         log.debug("[Skuber Watch (" + name + "): building request")
       val wsReq = k8sContext.buildRequest(Some(kind.urlPathComponent), Some(name), watch=true).withRequestTimeout(300000)
       if (log.isDebugEnabled) 
         log.debug("[Skuber Watch (" + name + "): ")
       val maybeResourceVersionParam = sinceResourceVersion map { "resourceVersion" -> _ }
       val watchRequest = maybeResourceVersionParam map { wsReq.withQueryString(_) } getOrElse(wsReq)
 
       if (log.isDebugEnabled) 
         log.debug("[Skuber Watch (" + name + "): creating joined iterate/enumerator pair for response stream")
       val (responseBytesIteratee, responseBytesEnumerator) = Concurrent.joined[Array[Byte]]
       
       // Execute the watch request and pipe the response through transformations that turn it 
       // from a stream of bytes into a stream of Json values into a stream of parsed object 
       // updates of the expected type
      if (log.isDebugEnabled) 
         log.debug("[Skuber Watch (" + name + "): making request to server")
       watchRequest.get(_ => responseBytesIteratee).flatMap(_.run)
       
       fromBytesEnumerator(name, responseBytesEnumerator)  
    }
    
    def fromBytesEnumerator[O <: ObjectResource](
        watchId: String,
        bytes : Enumerator[Array[Byte]])
        (implicit format: Format[O], kind: ObjKind[O], ec: ExecutionContext) : Enumerator[WatchEvent[O]] = 
    {
       if (log.isDebugEnabled)         
         log.debug("[Skuber Watch (" + watchId + "): building event enumerator from bytes enumerator")  
       bytes &>
         Encoding.decode() &> 
         Enumeratee.map { x =>  
           // purely for debugging
           if (log.isDebugEnabled) 
             log.debug("[Skuber Watch (" + watchId + "): handling chunk :'" + x.mkString + "'")
           x  
         } &>
         Enumeratee.grouped(WatchResponseJsonParser.jsonObject) &>
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
        k8sContext: K8SRequestContext,
        obj: O)
        (implicit format: Format[O], kind: ObjKind[O],ec: ExecutionContext) : Enumerator[WatchEvent[O]] =
    {
      events(k8sContext, obj.name, Option(obj.metadata.resourceVersion).filter(_.trim.nonEmpty))  
    }
    
}

object WatchResponseJsonEnumeratees {
  
    def jsObjects(watchId : String): Enumeratee[CharString, JsObject] =  jsObjects(watchId, WatchResponseJsonParser.jsonObject)
           
    def jsObjects(watchId: String, jsonObjectParser: Iteratee[CharString, JsObject]) = new Enumeratee[CharString, JsObject] {
      def step[A](inner: Iteratee[JsObject, A])(in: Input[JsObject]): Iteratee[JsObject, Iteratee[JsObject, A]] = in match {
        case Input.EOF => {
          if (Watch.log.isDebugEnabled) 
              Watch.log.debug("[Skuber Watch (" + watchId + ") : json objects iteratee reached EOF")
           Done(inner, in)
        }
        case _ => {
          if (Watch.log.isDebugEnabled) 
              Watch.log.debug("[Skuber Watch (" + watchId + ") : iteratee - received input " + in)
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
                Watch.log.debug("[Skuber Watch (" + watchId + ") : iteratee - about to parse next object")
      fed <- jsonObjectParser.map(jsObj => Iteratee.flatten(jsonObjectHandler.feed(Input.El(jsObj))))
      log2 = if (Watch.log.isDebugEnabled)
                Watch.log.debug("[Skuber Watch (" + watchId + ") : iteratee - fed object : " + fed)
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
                Watch.log.debug("[Skuber Watch: iteratee - in json object parser: parsing key beginning with '" + ch.getOrElse("") + "'")
         JsonParser.jsonKeyValues(keyValuesHandler, valueHandler)
       }
     }
     _ <- skipWhitespace 
    } yield keyValues
}