package skuber.examples.argo

import akka.actor.ActorSystem
import play.api.libs.json.{Format, Json}
import skuber.examples.argo.EventBusExample.EventBus.{Native, Nats}
import skuber.{CustomResource, ResourceDefinition, k8sInit}
import scala.concurrent.ExecutionContextExecutor

object EventBusExample extends App {
  type EventBusResource = CustomResource[EventBus.Spec, EventBus.Status]

  object EventBus {
    case class Spec(nats: Nats)
    case class Nats(native: Native)
    case class Native()
    case class Status()

    implicit val nativeFmt: Format[Native] = Json.format[Native]
    implicit val natsFmt: Format[Nats] = Json.format[Nats]
    implicit val specFmt: Format[Spec] = Json.format[Spec]
    implicit val statusFmt: Format[Status] = Json.format[Status]
    implicit val eventBusResourceDefinition = ResourceDefinition[EventBusResource](
      group = "argoproj.io",
      version = "v1alpha1",
      kind = "EventBus"
    )

    // Convenience method for constructing custom resources of the required type from a name
    def apply(name: String, spec: Spec) = CustomResource[Spec, Status](spec).withName(name)
  }

  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher
  val k8s = k8sInit

  val eventBusSpec = EventBus.Spec(Nats(Native()))
  val eventBusResource1 = EventBus("eventBusName", eventBusSpec)
  k8s.create(eventBusResource1)

}
