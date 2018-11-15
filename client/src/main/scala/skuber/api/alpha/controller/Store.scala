package skuber.api.alpha.controller

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import skuber.ObjectResource

import scala.collection.Map

/**
  * A Store encapsulates a client-side cache of resources, with any number of user specified indexes
  */
class Store[P, O <: ObjectResource](cache: Map[P, O])(
  implicit sys: ActorSystem, mat: Materializer)
{
  import Store._

  private val indexes: AtomicReference[List[Index[P,O,_]]] = new AtomicReference(List())

  /*
   * Add an index to the store. This will create a new index on the store which can be used by the application
   * to retrieve values efficiently according to their specific use case. Multiple indexes can be created.
   * The application can use the returned lookup function to retrieve store entries via the index.
   * @tparam I The type of the key to be used with the index to map store entries
   * @param indexer A function that the store will use to create keys in the index for new store entries
   * @return A function that the application can use to lookup indexed values in the store
   */
  def addIndex[I](indexer: Indexer[P,O,I]): IndexLookup[P,O,I] = {
    val index=new Index[P,O,I](indexer)
    val oldIndexes=indexes.get()
    indexes.compareAndSet(oldIndexes,oldIndexes :+ index)
    index
  }


    lazy val flow = Flow[Reflected[O]].map {
      case Added(o) =>
      case Updated(o) =>
      case Deleted(o) =>
      case StartReplacing(listVersion) =>
      case Replacement(listVersion, o)  =>
      case EndReplacing(listVersion) =>
    }
}

object Store {

  type StoreEntry[P,O]=(P,O)
  type Indexer[P,O,I] = StoreEntry[P,O] => Iterable[I]
  type IndexLookup[P,O,I]= I => Map[P,O]

  /**
   * The internal index implementation used by the store
   * @tparam P primary key type for the store
   * @tparam O the type of the values held by the store
   * @tparam I user-defined type (typically a String or case class) that keys the store entries in the index
   * @param indexer user-supplied function that indexes a specified store entry
   */
  class Index[P,O,I](indexer: Indexer[P,O,I]) extends IndexLookup[P,O,I] {

    val mapping: AtomicReference[Map[I, Map[P, O]]] = new AtomicReference(Map())

    // retrieve indexed entry by key I
    def apply(indexKey: I): Map[P, O] = mapping.get.get(indexKey).getOrElse(Map())

    def add(newStoreEntry: StoreEntry[P, O]): Unit = {
      indexer.apply(newStoreEntry).map { indexKey: I =>
        val oldMapping = mapping.get()
        val currentlyIndexed: Map[P, O] = oldMapping.get(indexKey).getOrElse(Map())
        val newIndexed = currentlyIndexed + newStoreEntry
        mapping.compareAndSet(oldMapping, oldMapping + (indexKey -> newIndexed))
      }
    }

    def remove(oldStoreEntry: StoreEntry[P, O]): Unit = {
      indexer.apply(oldStoreEntry).map { indexKey: I =>
        val oldMapping = mapping.get()
        val currentlyIndexed: Option[Map[P, O]] = oldMapping.get(indexKey)
        currentlyIndexed match {
          case None => // the store entry is not indexed - no action
          case Some(map) =>
            val newMapping = oldMapping - (indexKey)
            mapping.compareAndSet(oldMapping, newMapping)
        }
      }
    }
  }
}
