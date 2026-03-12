package skuber.catseffect.internal

import cats.effect.Async
import fs2.Stream
import play.api.libs.json.Format
import skuber.api.client.{AuthInfo, ListOptions, LoggingContext, Status, WatchEvent, WatchParameters}
import skuber.catseffect.CatsWatcher
import skuber.model.{ObjectResource, ResourceDefinition}

private[catseffect] class CatsWatcherImpl[F[_]: Async, O <: ObjectResource](
  backend: HttpBackend[F],
  clusterServer: String,
  namespace: String,
  auth: AuthInfo
) extends CatsWatcher[F, O]:

  override protected def _watch(
    watchRequestOptions: ListOptions,
    clusterScope: Boolean,
    bufSize: Int,
    errorHandler: Option[String => ?]
  )(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Stream[F, Either[Status, WatchEvent[O]]] =
    val params = WatchParameters(
      clusterScope = clusterScope,
      resourceVersion = watchRequestOptions.resourceVersion,
      labelSelector = watchRequestOptions.labelSelector,
      fieldSelector = watchRequestOptions.fieldSelector,
      timeoutSeconds = watchRequestOptions.timeoutSeconds,
      allowWatchBookmarks = watchRequestOptions.allowWatchBookmarks.getOrElse(false),
      sendInitialEvents = watchRequestOptions.sendInitialEvents.getOrElse(false),
      resourceVersionMatch = watchRequestOptions.resourceVersionMatch
    )
    WatchStream.watch[F, O](backend, clusterServer, namespace, auth, params)
