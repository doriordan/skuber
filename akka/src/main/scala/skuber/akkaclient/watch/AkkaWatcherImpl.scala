package skuber.akkaclient.watch

import play.api.libs.json.Format
import skuber.akkaclient.AkkaWatcher
import skuber.akkaclient.impl.AkkaKubernetesClientImpl
import skuber.api.client.{ListOptions, LoggingContext, WatchEvent, Watcher}
import skuber.model.{ObjectResource, ResourceDefinition}

class AkkaWatcherImpl[O <: ObjectResource](client: AkkaKubernetesClientImpl) extends AkkaWatcher[O]
{
  override val defaultBufSize: Int = 100000
  override val defaultWatchRequestTimeoutSeconds: Long = 30

  override protected def _watch(watchRequestOptions: ListOptions, clusterScope: Boolean, bufSize: Int, errorHandler: Option[(String) => _])(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext):  EventSource =
  {
    val pool = client.buildLongPollingPool[O]()
    WatchSource[O](client, pool, watchRequestOptions, bufSize, errorHandler, None, Some(clusterScope))
  }
}

