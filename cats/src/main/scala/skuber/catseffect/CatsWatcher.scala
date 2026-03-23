package skuber.catseffect

import fs2.Stream
import skuber.api.client.{K8SException, WatchEvent}
import skuber.api.client.Watcher
import skuber.model.ObjectResource

trait CatsWatcher[F[_], O <: ObjectResource] extends Watcher[O]:
  override type EventSource = Stream[F, Either[K8SException, WatchEvent[O]]]
