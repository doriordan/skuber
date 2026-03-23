package skuber.zio.internal

import zio.*
import zio.stream.*
import play.api.libs.json.Format
import skuber.api.client.{AuthInfo, WatchEvent, WatchParameters}
import skuber.internal.{HttpMethod, K8sRequest, UrlBuilder}
import skuber.model.{ObjectResource, ResourceDefinition}
import skuber.api.client.K8SException
import skuber.api.client.Status

private[zio] object WatchStream:

  def watch[O <: ObjectResource](
    backend: HttpBackend,
    clusterServer: String,
    namespace: String,
    auth: AuthInfo,
    params: WatchParameters
  )(using fmt: Format[O], rd: ResourceDefinition[O]): ZStream[Any, K8SException, WatchEvent[O]] =

    given Format[WatchEvent[O]] = skuber.json.format.apiobj.watchEventFormat[O]

    def buildQueryParams(resourceVersion: Option[String]): Seq[(String, String)] =
      val base = Seq("watch" -> "true")
      val rv   = resourceVersion.orElse(params.resourceVersion).map("resourceVersion" -> _).toSeq
      val ls   = params.labelSelector.map(s => "labelSelector" -> s.toString).toSeq
      val fs   = params.fieldSelector.map(s => "fieldSelector" -> s).toSeq
      val ts   = params.timeoutSeconds.map(t => "timeoutSeconds" -> t.toString).toSeq
      val awb  = if params.allowWatchBookmarks then Seq("allowWatchBookmarks" -> "true") else Seq.empty
      val sie  = if params.sendInitialEvents then Seq("sendInitialEvents" -> "true") else Seq.empty
      val rvm  = params.resourceVersionMatch.map(m => "resourceVersionMatch" -> m).toSeq
      base ++ rv ++ ls ++ fs ++ ts ++ awb ++ sie ++ rvm

    def singleSession(resourceVersion: Option[String]): ZStream[Any, K8SException, WatchEvent[O]] =
      val url = UrlBuilder.resourceUrl(
        clusterServer, namespace, rd,
        clusterScopeOverride = if params.clusterScope then Some(true) else None
      )
      val req = K8sRequest(HttpMethod.Get, url, queryParams = buildQueryParams(resourceVersion))
      ZStream.fromZIO(AuthInterceptor.addAuth(req, auth)
        .mapError(e => new K8SException(Status(message = Some(e.getMessage), code = Some(0)))))
        .flatMap: authedReq =>
          PlayJsonBridge.parseJsonLines[WatchEvent[O]](backend.streamRequest(authedReq))
            .flatMap:
              case Right(event) => ZStream.succeed(event)
              case Left(err)    => ZStream.fromZIO(ZIO.logWarning(s"Watch parse error for ${rd.spec.names.kind}: $err")) *> ZStream.empty

    def go(resourceVersion: Option[String]): ZStream[Any, K8SException, WatchEvent[O]] =
      ZStream.fromZIO(Ref.make(resourceVersion)).flatMap: rvRef =>
        singleSession(resourceVersion)
          .tap: event =>
            val rv = event._object.metadata.resourceVersion
            rvRef.set(Some(rv)).when(rv.nonEmpty)
          .catchAllCause: _ =>
            // Reconnect on any error or defect (including network drops converted to defects by orDie)
            ZStream.fromZIO(rvRef.get).flatMap(go)
          .concat:
            // Reconnect after clean server-side close
            ZStream.fromZIO(rvRef.get).flatMap(go)

    go(params.resourceVersion)
