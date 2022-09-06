package skuber

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Event(val kind: String = "Event",
                 override val apiVersion: String = v1,
                 val metadata: ObjectMeta,
                 involvedObject: ObjectReference,
                 reason: Option[String] = None,
                 message: Option[String] = None,
                 source: Option[Event.Source] = None,
                 firstTimestamp: Option[Timestamp] = None,
                 lastTimestamp: Option[Timestamp] = None,
                 count: Option[Int] = None,
                 `type`: Option[String] = None)
  extends ObjectResource

object Event {

  val specification: CoreResourceSpecification = CoreResourceSpecification(scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(plural = "events",
      singular = "event",
      kind = "Event",
      shortNames = List("ev")))
  implicit val evDef: ResourceDefinition[Event] = new ResourceDefinition[Event] {
    def spec: ResourceSpecification = specification
  }
  implicit val evListDef: ResourceDefinition[EventList] = new ResourceDefinition[EventList] {
    def spec: ResourceSpecification = specification
  }

  case class Source(component: Option[String] = None, host: Option[String] = None)
}