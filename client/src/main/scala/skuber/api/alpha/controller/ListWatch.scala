package skuber.api.alpha.controller

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import skuber.api.client.KubernetesClient
import skuber.{K8SWatchEvent, LabelSelector, ListOptions, ListResource, ObjectResource, ResourceDefinition}

import scala.concurrent.{ExecutionContext, Future}


/**
  * @author David O'Riordan
  *
  *  A ListWatch provides list and watch functions that combined enable reflectors to keep a store in sync with a set of
  *  resources on the cluster.
  */

trait ListWatch[O <: ObjectResource] {
  val list: ListWatch.ResourceLister[O]
  val watch: ListWatch.ResourceWatcher[O]
}

object ListWatch {

  // A ResourceLister function returns a source that emits the current values of a set of objects of type O on the cluster
  // in one or more chunks (with the current resource version when the list was requested)
  type ResourceVersion = String
  type ResourceListSource[O <: ObjectResource] = Source[ListChunk[O],_]
  case class ListResult[O <: ObjectResource](resourceVersion: ResourceVersion, resourceListSource: ResourceListSource[O])
  type ResourceLister[O <: ObjectResource] = () => Future[ListResult[O]]

  // ResourceWatcher function type takes an optional resource version  and returns a source of Kubernetes
  // events on the resources in scope since that resource version (or all events if not set)
  type ResourceWatchSource[O <:ObjectResource] = Source[K8SWatchEvent[O],_]
  type ResourceWatcher[O <: ObjectResource] = Option[String] =>  ResourceWatchSource[O]

  /**
    * Provides a factory of convenient ListWatch implementations for use with reflectors
    * Note: currently all listers here return the full list in a single chunk - listers will be added
    * that use the capability to specify a limit option to retrieve and emit the list in chunks.
    */
  class ListWatchers[O <: ObjectResource](val k8s: KubernetesClient)(
    implicit val system: ActorSystem,
    val mat: Materializer,
    val ofmt: Format[O],
    val lmt: Format[ListResource[O]],
    val rd: ResourceDefinition[O])
  {
    implicit val executor: ExecutionContext = system.dispatcher

    private def unchunkedLister(klistgetter: => Future[ListResource[O]]): ResourceLister[O] = () => klistgetter.map { l =>
      ListResult(l.resourceVersion, Source.single(ListChunk(l.resourceVersion, l.items)))
    }

    lazy val allResourceLister: ResourceLister[O] = unchunkedLister(k8s.list[ListResource[O]])
    lazy val allResourceWatcher: ResourceWatcher[O] = (since: Option[String]) => {
      val options = ListOptions(resourceVersion =  since)
      k8s.watchWithOptions[O](options)
    }

    lazy val listWatchAll = new ListWatch[O] {
      override val list = allResourceLister
      override val watch = allResourceWatcher
    }

    def labelledResourceLister(ls: LabelSelector): ResourceLister[O] = unchunkedLister(k8s.listSelected[ListResource[O]](ls))

    def labelledResourceWatcher(labelSelector: LabelSelector): ResourceWatcher[O] = (since: Option[String]) => {
      val options = ListOptions(resourceVersion = since, labelSelector = Some(labelSelector))
      k8s.watchWithOptions[O](options)
    }

    class ListWatchSelected(ls: LabelSelector) extends ListWatch[O] {
      override val list = labelledResourceLister(ls)
      override val watch = labelledResourceWatcher(ls)
    }

    def listerWithOptions(options: ListOptions): ResourceLister[O] = unchunkedLister(k8s.listWithOptions[ListResource[O]](options))

    def watcherWithOptions(options: ListOptions): ResourceWatcher[O] = (since: Option[String]) => {
      val updatedOptions = options.copy(resourceVersion = since)
      k8s.watchWithOptions[O](updatedOptions)
    }

    class ListWatchWithOptions(options: ListOptions) extends ListWatch[O] {
      override val list = listerWithOptions(options)
      override val watch = watcherWithOptions(options)
    }
  }

  def listWatchers[O <: ObjectResource](k8s: KubernetesClient)(
    implicit system: ActorSystem, mat: Materializer, ofmt: Format[O], fmt: Format[ListResource[O]], rd: ResourceDefinition[O]
  ) = new ListWatchers[O](k8s)
}
