package skuber

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Event(
  	val kind: String ="Event",
    val metadata: ObjectMeta,
    override val apiVersion: String = v1,
    involvedObject: ObjectReference,
    reason: Option[String] = None,
    message: Option[String] = None,
    source: Option[Event.Source] = None,
    firstTimestamp: Option[Timestamp] = None,
    lastTimestamp: Option[Timestamp] = None,
    count: Option[Int] = None) 
  extends ObjectResource

object Event {

  val specification=CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(
      plural="events",
      singular="event",
      kind="Event",
      shortNames=List("ev")
    )
  )
  implicit val evDef = new ResourceDefinition[Event] { def spec=specification }
  implicit val evListDef = new ResourceDefinition[EventList] { def spec=specification }

  case class Source(component: Option[String] = None, host: Option[String] = None)
}