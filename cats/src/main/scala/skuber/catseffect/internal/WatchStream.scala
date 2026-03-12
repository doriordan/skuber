package skuber.catseffect.internal

import cats.effect.{Async, Ref}
import cats.syntax.all.*
import fs2.Stream
import org.slf4j.LoggerFactory
import play.api.libs.json.Format
import skuber.api.client.{Status, WatchEvent, WatchParameters}
import skuber.model.{ObjectResource, ResourceDefinition}

private[catseffect] object WatchStream:

  private val log = LoggerFactory.getLogger("skuber.api")

  def watch[F[_]: Async, O <: ObjectResource](
    backend: HttpBackend[F],
    clusterServer: String,
    namespace: String,
    auth: skuber.api.client.AuthInfo,
    params: WatchParameters
  )(using fmt: Format[O], rd: ResourceDefinition[O]): Stream[F, Either[Status, WatchEvent[O]]] =

    given Format[WatchEvent[O]] = skuber.json.format.apiobj.watchEventFormat[O]

    def buildQueryParams(resourceVersion: Option[String]): Map[String, String] =
      val base = Map("watch" -> "true")
      val rv = resourceVersion.orElse(params.resourceVersion).map("resourceVersion" -> _)
      val ls = params.labelSelector.map(s => "labelSelector" -> s.toString)
      val fs = params.fieldSelector.map(s => "fieldSelector" -> s)
      val ts = params.timeoutSeconds.map(t => "timeoutSeconds" -> t.toString)
      val awb = if params.allowWatchBookmarks then Some("allowWatchBookmarks" -> "true") else None
      val sie = if params.sendInitialEvents then Some("sendInitialEvents" -> "true") else None
      val rvm = params.resourceVersionMatch.map(m => "resourceVersionMatch" -> m)

      base ++ rv ++ ls ++ fs ++ ts ++ awb ++ sie ++ rvm

    def singleSession(resourceVersion: Option[String]): Stream[F, Either[Status, WatchEvent[O]]] =
      val url = UrlBuilder.resourceUrl(
        clusterServer, namespace, rd,
        clusterScopeOverride = if params.clusterScope then Some(true) else None
      )
      val queryParams = buildQueryParams(resourceVersion)
      val req = K8sRequest(method = HttpMethod.Get, url = url, queryParams = queryParams)

      if log.isDebugEnabled then
        log.debug(s"Watch session starting for ${rd.spec.names.kind} (resourceVersion=${resourceVersion.getOrElse("none")})")

      Stream.eval(AuthInterceptor.addAuth[F](req, auth)).flatMap: authedReq =>
        PlayJsonBridge.parseJsonLines[F, WatchEvent[O]](backend.streamRequest(authedReq)).map:
          case Right(event) => Right(event)
          case Left(err) =>
            if log.isWarnEnabled then
              log.warn(s"Watch event parse error for ${rd.spec.names.kind}: $err")
            Left(Status(message = Some(s"Watch event parse error: $err")))

    def go(resourceVersion: Option[String]): Stream[F, Either[Status, WatchEvent[O]]] =
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
