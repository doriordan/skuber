package skuber

/**
  * @author David O'Riordan
  */
case class Event(
    kind: String = "Event",
    override val apiVersion: String = v1,
    metadata: ObjectMeta,
    involvedObject: ObjectReference,
    reason: Option[String] = None,
    message: Option[String] = None,
    source: Option[Event.Source] = None,
    firstTimestamp: Option[Timestamp] = None,
    lastTimestamp: Option[Timestamp] = None,
    count: Option[Int] = None,
    `type`: Option[String] = None
) extends ObjectResource

object Event {

  val specification = CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(
      plural = "events",
      singular = "event",
      kind = "Event",
      shortNames = List("ev")
    )
  )
  implicit val evDef: ResourceDefinition[Event] = new ResourceDefinition[Event] {
    def spec: CoreResourceSpecification = specification
  }
  implicit val evListDef: ResourceDefinition[EventList] = new ResourceDefinition[EventList] {
    def spec: CoreResourceSpecification = specification
  }

  case class Source(component: Option[String] = None, host: Option[String] = None)
}
