package skuber.api.alpha.controller

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import skuber.api.client.EventType
import skuber.{K8SWatchEvent, ObjectResource}

/**
  * Reflector synchronises the state of a set of objects in the cluster with some stateful downstream sink (usually a Store)
  * The reflector is supplied with a ListWatch for accessing the required set of objects on the cluster.
  *
  * Reflector consists of these main flows:
  *
  * 1. Replace
  *    This turns a source of chunks of objects (as output by the result of a ListWatcher list call) into a source of
  *    Replacements bounded at beginning and end by StartReplacing and EndReplacing respectively.
  *    The downstream source can use these replace events to fully replace its state.
  * 2. Deltas
  *    This  transforms a source of Kubernetes watch events (as returned by the result of a ListWatcher watch call)
  *    into a source of deltas.
  *    The downstream sink can use these deltas to keep its state in sync with the cluster.
  * 3. Command
  *    Takes a command which must be one of DoReplace, DoDeltas or DoSynchronise:
  *    The DoReplace command calls the listwatch lister, routing the returned chunks through the Replace flow
  *    The DoDeltas command calls the listwatch watcher with a spcified resource version, routing the returned events through
  *    the Deltas flow.
  *    The DoReflect command submits a DoReplace, followed by a DoDeltas (from the resource version returned by the DoReplace)
  *    command to ensure any updates to the objects on the cluster subsequent to the replace are notified to the downstream sink.
  *
  *    Normally users of Reflector only need to submit a DoReflect, as it satisfies the most common use case of enabling
  *    downstream to maintain a complete and up to date copy of the current state of the applicable set of object resources.
  */
class Reflector[O <: ObjectResource](listWatch: ListWatch[O])(implicit sys: ActorSystem, mat: Materializer)
{
  import Reflector._

  implicit val executor = sys.dispatcher

  lazy val replaceFlow: Flow[ListChunk[O], ReplaceEvent[O], NotUsed] = Flow[ListChunk[O]].prefixAndTail(1).flatMapConcat { case (first, rest) =>
    // we split into prefix and tail so that we can extract the resource version to be emitted in
    // all replace events from the first chunk
    val resourceVersion = first.head.resourceVersion
    def replaceChunk(chunk: ListChunk[O]) = chunk.elements.map(obj => Replacement(resourceVersion, obj))

    // output replacement events for each set of objects in each chunk, demarcated by StartReplacing and EndReplacing
    val start = Source.single(StartReplacing[O](resourceVersion))
    val firstChunkOfReplacements = Source.fromIterator(() => replaceChunk(first.head).toIterator)
    val restOfReplacements: Source[Replacement[O], NotUsed] = rest.mapConcat(replaceChunk(_))
    val end = Source.single(EndReplacing[O](resourceVersion))
    start ++ firstChunkOfReplacements ++ restOfReplacements ++ end
  }

  lazy val deltasFlow: Flow[K8SWatchEvent[O], Delta[O], NotUsed] = Flow[K8SWatchEvent[O]].map { k8sEvent =>
    k8sEvent._type match {
      case EventType.ADDED => Added(k8sEvent._object)
      case EventType.MODIFIED => Updated(k8sEvent._object)
      case EventType.DELETED => Deleted(k8sEvent._object)
    }
  }

  lazy val commandFlow: Flow[Command, Reflected[O], NotUsed] = Flow[Command].flatMapConcat {
    case DoReplace => currentListSource.via(replaceFlow)
    case DoDeltas(resourceVersion) => watchFrom(resourceVersion).via(deltasFlow)
    case DoReflect => Source.fromFutureSource(listWatch.list().map { l =>
      val replace = l.resourceListSource.via(replaceFlow)
      val deltas = watchFrom(l.resourceVersion).via(deltasFlow)
      replace.concat(deltas)
    })
  }

  private def currentListSource = {
    Source.fromFutureSource((listWatch.list().map(_.resourceListSource)))
  }

  private def watchFrom(sinceResourceVersion: String) = {
    listWatch.watch(Some(sinceResourceVersion))
  }
}

object Reflector {

  sealed trait Command

  case object DoReplace extends Command
  case class DoDeltas(sinceResourceVersion: String) extends Command
  case object DoReflect extends Command

}