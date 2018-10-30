package skuber.api.alpha

import skuber.ObjectResource

/**
  * @author David O'Riordan
  *
  * (NOTE: The features and API in the controller package are currently in alpha / experimental stage, and are planned
  * to be moved from `skuber.api.alpha.controller' to 'skuber.api.controller' when stable)
  *
  * The Kubernetes client-go library supports a set of components that reflects updates to resources on the
  * cluster into a local (client-side) cache, and informs controllers of changes to those resources.
  *
  * These components are described here:
  * https://github.com/kubernetes/sample-controller/blob/master/docs/controller-client-go.md
  *
  * This package supports similar functionality for Scala-based Kubernetes controllers, albeit with a very different
  * design leveraging Akka streams to reactively handle the flow of updates from the cluster through the cache and
  * informers.
  *
  * The primary use case is to make it easier to write custom controllers (as used for example by Kubernetes operators)
  * that are reliably updated with changes to resources as well as benefitting from local caching of those resources.
  */
package object controller {

  // A Reflected is an event that can be produced by a reflector based on listing/watching a set of objects on the cluster
  sealed trait Reflected[O <: ObjectResource]

  // Replace events are emitted by the reflector te tell downstream to fully replace its copy of the state of the
  // applicable set of objects
  sealed abstract class ReplaceEvent[O <: ObjectResource](listResourceVersion: String) extends Reflected[O]
  case class StartReplacing[O <: ObjectResource](listResourceVersion: String) extends ReplaceEvent[O](listResourceVersion)
  case class Replacement[O <: ObjectResource](listResourceVersion: String, o: O) extends ReplaceEvent[O](listResourceVersion)
  case class EndReplacing[O <: ObjectResource](listResourceVersion: String) extends ReplaceEvent[O](listResourceVersion)

  // A Delta is emitted when an upstream change has occurred to an object, can be emitted by Reflector or Store
  sealed trait Delta[O <: ObjectResource] extends Reflected[O] with Inform[O]
  case class Added[O <: ObjectResource](o: O) extends Delta[O]
  case class Updated[O <: ObjectResource](o: O) extends Delta[O]
  case class Deleted[O <: ObjectResource](o: O) extends Delta[O]


  // An Inform is an event that can be sent to Informers - either deltas to objects or sync events
  sealed trait Inform[o <: ObjectResource]

  // SyncEvents are emitted when a (re)sync command is sent to a Store - a Sync is emitted for each object in the store
  sealed trait SyncEvent[O <: ObjectResource] extends Inform[O]
  case class StartSyncing[O <: ObjectResource](syncId: Option[String]) extends SyncEvent[O]
  case class Sync[O <: ObjectResource](syncId: Option[String], o: O) extends SyncEvent[O]
  case class EndSyncing[O <: ObjectResource](syncId: Option[String]) extends SyncEvent[O]

  // ListChunks are output by ListWatch listers, which emit one to many chunks for
  // each list call. This enables listers to be created that retrieve a list in chunks from
  // the API server
  case class ListChunk[O <: ObjectResource](resourceVersion: String,elements: List[O])
}
