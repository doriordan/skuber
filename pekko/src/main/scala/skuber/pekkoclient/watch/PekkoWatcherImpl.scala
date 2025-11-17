package skuber.pekkoclient.watch

import play.api.libs.json.Format
import skuber.pekkoclient.PekkoWatcher
import skuber.pekkoclient.impl.PekkoKubernetesClientImpl
import skuber.api.client.{ListOptions, LoggingContext, WatchEvent, Watcher}
import skuber.model.{ObjectResource, ResourceDefinition}

class PekkoWatcherImpl[O <: ObjectResource](client: PekkoKubernetesClientImpl) extends PekkoWatcher[O]
{
  override protected def _watch(watchRequestOptions: ListOptions, clusterScope: Boolean, bufSize: Int, errorHandler: Option[(String) => _])(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext):  EventSource =
  {
    val pool = client.buildLongPollingPool[O]()
    WatchSource[O](client, pool, watchRequestOptions, bufSize, errorHandler, None, Some(clusterScope))
  }
}

