package skuber.model

import coretypes._

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Event(
  	val kind: String ="Event",
  	override val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    involvedObject: ObjectReference,
    reason: Option[String] = None,
    message: Option[String] = None,
    source: Option[Event.Source] = None,
    firstTimestamp: Option[Timestamp] = None,
    lastTimestamp: Option[Timestamp] = None,
    count: Option[Int] = None) 
  extends ObjectResource with KListItem

object Event {
  case class Source(component: Option[String] = None, host: Option[String] = None)
}