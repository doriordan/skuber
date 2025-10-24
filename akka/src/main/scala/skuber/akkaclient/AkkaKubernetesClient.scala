package skuber.akkaclient

import akka.stream.scaladsl.Source
import skuber.api.client.{KubernetesClient, WatchEvent, Watcher}
import skuber.model.ObjectResource

/**
  * These traits extend the generic KubernetesClient trait with the Akka streams specific support for streaming
  * operations of the API (executing commands, streaming pod logs, and watching for events).
  */
trait AkkaKubernetesClient extends KubernetesClient[AkkaSB, AkkaSI, AkkaSO] {
  def getWatcher[O <: ObjectResource]: AkkaWatcher[O]
}

trait AkkaWatcher[O <: ObjectResource] extends Watcher[O] {
  override type EventSource = Source[WatchEvent[O], _] // type of event source returned by watch requests
}