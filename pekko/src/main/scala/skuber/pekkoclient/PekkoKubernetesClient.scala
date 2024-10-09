package skuber.pekkoclient

import org.apache.pekko.stream.scaladsl.Source
import skuber.api.client.{KubernetesClient, WatchEvent, Watcher}
import skuber.model.ObjectResource

/**
  * These traits provide the full skuber API for the Pekko-based client
  */
trait PekkoKubernetesClient extends KubernetesClient[PekkoSB, PekkoSI, PekkoSO] {
  def getWatcher[O <: ObjectResource]: PekkoWatcher[O]
}

trait PekkoWatcher[O <: ObjectResource] extends Watcher[O] {
  override type EventSource = Source[WatchEvent[O], _] // type of event sources returned by watch requests
}