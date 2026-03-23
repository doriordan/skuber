package skuber.catseffect.internal

import cats.effect.{Async, Ref}
import cats.syntax.all.*
import fs2.Stream
import org.slf4j.LoggerFactory
import play.api.libs.json.Format
import skuber.api.client.{K8SException, Status, WatchEvent, WatchParameters}
import skuber.internal.{AuthInterceptor, HttpMethod, K8sRequest, UrlBuilder}
import skuber.model.{ObjectResource, ResourceDefinition}

private[catseffect] object WatchStream:

  private val log = LoggerFactory.getLogger("skuber.api")

  def watch[F[_]: Async, O <: ObjectResource](
    backend: HttpBackend[F],
    clusterServer: String,
    namespace: String,
    auth: skuber.api.client.AuthInfo,
    params: WatchParameters
  )(using fmt: Format[O], rd: ResourceDefinition[O]): Stream[F, Either[K8SException, WatchEvent[O]]] =

    given Format[WatchEvent[O]] = skuber.json.format.apiobj.watchEventFormat[O]

    def buildQueryParams(resourceVersion: Option[String]): Seq[(String, String)] =
      val base = Seq("watch" -> "true")
      val rv  = resourceVersion.orElse(params.resourceVersion).map("resourceVersion" -> _).toSeq
      val ls  = params.labelSelector.map(s => "labelSelector" -> s.toString).toSeq
      val fs  = params.fieldSelector.map(s => "fieldSelector" -> s).toSeq
      val ts  = params.timeoutSeconds.map(t => "timeoutSeconds" -> t.toString).toSeq
      val awb = if params.allowWatchBookmarks then Seq("allowWatchBookmarks" -> "true") else Seq.empty
      val sie = if params.sendInitialEvents then Seq("sendInitialEvents" -> "true") else Seq.empty
      val rvm = params.resourceVersionMatch.map(m => "resourceVersionMatch" -> m).toSeq
      base ++ rv ++ ls ++ fs ++ ts ++ awb ++ sie ++ rvm

    def singleSession(resourceVersion: Option[String]): Stream[F, Either[K8SException, WatchEvent[O]]] =
      val url = UrlBuilder.resourceUrl(
        clusterServer, namespace, rd,
        clusterScopeOverride = if params.clusterScope then Some(true) else None
      )
      val queryParams = buildQueryParams(resourceVersion)
      val req = K8sRequest(method = HttpMethod.Get, url = url, queryParams = queryParams)

      if log.isDebugEnabled then
        log.debug(s"Watch session starting for ${rd.spec.names.kind} (resourceVersion=${resourceVersion.getOrElse("none")})")

      Stream.eval(Async[F].executionContext.flatMap { ec =>
        Async[F].fromFuture(Async[F].delay(AuthInterceptor.addAuth(req, auth)(using ec)))
      }).flatMap: authedReq =>
        PlayJsonBridge.parseJsonLines[F, WatchEvent[O]](backend.streamRequest(authedReq)).map:
          case Right(event) => Right(event)
          case Left(err) =>
            if log.isWarnEnabled then
              log.warn(s"Watch event parse error for ${rd.spec.names.kind}: $err")
            Left(new K8SException(Status(message = Some(s"Watch event parse error: $err"))))

    def go(resourceVersion: Option[String]): Stream[F, Either[K8SException, WatchEvent[O]]] =
      Stream.eval(Ref.of[F, Option[String]](resourceVersion)).flatMap: rvRef =>
        singleSession(resourceVersion)
          .evalTap:
            case Right(event) =>
              val rv = event._object.metadata.resourceVersion
              if rv.nonEmpty then rvRef.set(Some(rv))
              else Async[F].unit
            case _ => Async[F].unit
          ++ Stream.eval(rvRef.get).flatMap: lastRv =>
            if log.isDebugEnabled then
              log.debug(s"Watch session ended for ${rd.spec.names.kind}, reconnecting (resourceVersion=${lastRv.getOrElse("none")})")
            go(lastRv)

    go(params.resourceVersion)
