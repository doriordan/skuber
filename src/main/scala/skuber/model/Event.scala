package skuber.model

import Model._

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Event(
  	val kind: String ="Event",
  	val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    involvedObject: ObjectReference,
    reason: Option[String] = None,
    message: Option[String] = None,
    source: Option[Event.Source] = None,
    firstTimestamp: Option[Date] = None,
    lastTimestamp: Option[Date] = None,
    count: Option[Int] = None) 
  extends ObjectResource with KListable

object Event {
  case class Source(component: Option[String] = None, host: Option[String] = None)
}