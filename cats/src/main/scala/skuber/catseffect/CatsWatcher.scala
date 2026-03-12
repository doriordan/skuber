package skuber.catseffect

import fs2.Stream
import skuber.api.client.{Status, WatchEvent}
import skuber.api.client.Watcher
import skuber.model.ObjectResource

trait CatsWatcher[F[_], O <: ObjectResource] extends Watcher[O]:
  override type EventSource = Stream[F, Either[Status, WatchEvent[O]]]
