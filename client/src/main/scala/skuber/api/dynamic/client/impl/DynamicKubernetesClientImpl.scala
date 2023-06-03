package skuber.api.dynamic.client.impl

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import play.api.libs.json.JsString
import skuber._
import skuber.api.client.{K8SException => _, _}
import skuber.api.security.{HTTPRequestAuth, TLS}
import skuber.api.watch.{LongPollingPool, WatchSource}
import skuber.json.PlayJsonSupportForAkkaHttp._
import skuber.json.format.apiobj.statusReads
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * This is non-typed kubernetes client, for typed client see [[skuber.api.client.impl.KubernetesClientImpl]]
  * This class provides a dynamic client for the Kubernetes API server.
  * It is intended to be used for accessing resources / classes that are not part of the skuber library.
  *
  * It uses the Akka HTTP client to handle the requests to
  * the Kubernetes API server.
  */
class DynamicKubernetesClientImpl(context: Context = Context(),
                                  logConfig: LoggingConfig,
                                  closeHook: Option[() => Unit],
                                  poolSettings: ConnectionPoolSettings)(implicit actorSystem: ActorSystem, executionContext: ExecutionContext) {
  private val namespaceName = "default"
  private val log = Logging.getLogger(actorSystem, "skuber.api")
  private val requestAuth = context.authInfo
  private val sslContext = TLS.establishSSLContext(context)
  private val connectionContext: HttpsConnectionContext = sslContext
    .map { ssl =>
      ConnectionContext.httpsClient { (host, port) =>
        val engine = ssl.createSSLEngine(host, port)
        engine.setUseClientMode(true)
        engine.setEnabledProtocols(Array("TLSv1.2", "TLSv1"))
        engine
      }
    }.getOrElse(Http().defaultClientHttpsContext)

  private val clusterServer = context.cluster.server
  private val clusterServerUri = Uri(clusterServer)

  private var isClosed = false

  private[skuber] def invoke(request: HttpRequest)(implicit lc: LoggingContext): Future[HttpResponse] = {
    if (isClosed) {
      logError("Attempt was made to invoke request on closed API request context")
      throw new IllegalStateException("Request context has been closed")
    }
    logInfo(logConfig.logRequestBasic, s"about to send HTTP request: ${request.method.value} ${request.uri.toString}")
    val responseFut = Http().singleRequest(request, connectionContext = connectionContext)
    responseFut onComplete {
      case Success(response) => logInfo(logConfig.logResponseBasic, s"received response with HTTP status ${response.status.intValue()}")
      case Failure(ex) => logError("HTTP request resulted in an unexpected exception", ex)
    }
    responseFut
  }


  private[skuber] def buildRequest(method: HttpMethod,
                                   apiVersion: String,
                                   resourcePlural: String,
                                   nameComponent: Option[String],
                                   query: Option[Uri.Query] = None,
                                   namespace: Option[String]): HttpRequest = {
    val nsPathComponent: Option[String] =
      namespace match {
        case Some(ns) => Some(s"namespaces/$ns")
        case None => Some(s"namespaces/$namespaceName")
      }

    val k8sUrlOptionalParts = List(clusterServer,
      "apis",
      apiVersion,
      nsPathComponent,
      resourcePlural,
      nameComponent)

    val k8sUrlParts = k8sUrlOptionalParts collect {
      case p: String if p != "" => p
      case Some(p: String) if p != "" => p
    }

    val k8sUrlStr = k8sUrlParts.mkString("/")

    val uri = query.map { q =>
      Uri(k8sUrlStr).withQuery(q)
    }.getOrElse {
      Uri(k8sUrlStr)
    }

    val req: HttpRequest = HttpRequest(method = method, uri = uri)
    HTTPRequestAuth.addAuth(req, requestAuth)
  }

  private[skuber] def logInfo(enabledLogEvent: Boolean, msg: => String)(implicit lc: LoggingContext) = {
    if (log.isInfoEnabled && enabledLogEvent) {
      log.info(s"[ ${lc.output} - ${msg}]")
    }
  }

  private[skuber] def logInfoOpt(enabledLogEvent: Boolean, msgOpt: => Option[String])(implicit lc: LoggingContext) = {
    if (log.isInfoEnabled && enabledLogEvent) {
      msgOpt foreach { msg =>
        log.info(s"[ ${lc.output} - ${msg}]")
      }
    }
  }

  private[skuber] def logWarn(msg: String)(implicit lc: LoggingContext) = {
    log.error(s"[ ${lc.output} - $msg ]")
  }

  private[skuber] def logError(msg: String)(implicit lc: LoggingContext) = {
    log.error(s"[ ${lc.output} - $msg ]")
  }

  private[skuber] def logError(msg: String, ex: Throwable)(implicit lc: LoggingContext) = {
    log.error(ex, s"[ ${lc.output} - $msg ]")
  }

  private[skuber] def logDebug(msg: => String)(implicit lc: LoggingContext) = {
    if (log.isDebugEnabled)
      log.debug(s"[ ${lc.output} - $msg ]")
  }

  private[skuber] def logRequestObjectDetails[O <: ObjectResource](method: HttpMethod, resource: O)(implicit lc: LoggingContext) = {
    logInfoOpt(logConfig.logRequestBasicMetadata, {
      val name = resource.name
      val version = resource.metadata.resourceVersion
      method match {
        case HttpMethods.PUT | HttpMethods.PATCH => Some(s"Requesting update of resource: { name:$name, version:$version ... }")
        case HttpMethods.POST => Some(s"Requesting creation of resource: { name: $name ...}")
        case _ => None
      }
    })
    logInfo(logConfig.logRequestFullObjectResource, s" Marshal and send: ${resource.toString}")
  }

  private[skuber] def logReceivedObjectDetails(resource: DynamicKubernetesObject)(implicit lc: LoggingContext) = {
    logInfo(logConfig.logResponseBasicMetadata, s" resource: { kind:${resource.kind} name:${resource.metadata.map(_.name)} version:${resource.metadata.map(_.resourceVersion)} ... }")
    logInfo(logConfig.logResponseFullObjectResource, s" received and parsed: ${resource.toString}")
  }

  private[skuber] def logReceivedListDetails[L <: ListResource[_]](result: L)(implicit lc: LoggingContext) = {
    logInfo(logConfig.logResponseBasicMetadata, s"received list resource of kind ${result.kind}")
    logInfo(logConfig.logResponseListSize, s"number of items in received list resource: ${result.items.size}")
    logInfo(logConfig.logResponseListNames, s"received ${result.kind} contains item(s): ${result.itemNames}]")
    logInfo(logConfig.logResponseFullListResource, s" Unmarshalled list resource: ${result.toString}")
  }

  private[skuber] def makeRequestReturningObjectResource(httpRequest: HttpRequest)(implicit lc: LoggingContext): Future[DynamicKubernetesObject] = {
    for {
      httpResponse <- invoke(httpRequest)
      result <- toKubernetesResponse(httpResponse)
      _ = logReceivedObjectDetails(result)
    } yield result
  }

  //  def create[O <: ObjectResource](obj: O, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] = {
  //    modify(HttpMethods.POST)(obj, namespace)
  //  }

  //  def getNamespaceNames(implicit lc: LoggingContext): Future[List[String]] = {
  //    list[NamespaceList].map { namespaceList =>
  //      val namespaces = namespaceList.items
  //      namespaces.map(_.name)
  //    }
  //  }


  private[skuber] def toKubernetesResponse(response: HttpResponse)(implicit lc: LoggingContext): Future[DynamicKubernetesObject] = {
    val statusOptFut = checkResponseStatus(response)
    statusOptFut flatMap {
      case Some(status) =>
        throw new K8SException(status)
      case None =>
        try {
          Unmarshal(response).to[DynamicKubernetesObject]
        }
        catch {
          case ex: Exception =>
            logError("Unable to unmarshal resource from response", ex)
            throw new K8SException(Status(message = Some("Error unmarshalling resource from response"), details = Some(JsString(ex.getMessage))))
        }
    }
  }

  /*
   * List objects of specific resource kind in current namespace
   */
  //  def list[L <: ListResource[_]](namespace: Option[String] = None)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] = {
  //    _list[L](rd, None, namespace)
  //  }
  //
  //  /*
  //   * Retrieve the list of objects of given type in the current namespace that match the supplied label selector
  //   */
  //  def listSelected[L <: ListResource[_]](labelSelector: LabelSelector, namespace: Option[String] = None)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] = {
  //    _list[L](rd, Some(ListOptions(labelSelector = Some(labelSelector))), namespace)
  //  }
  //
  //  def listWithOptions[L <: ListResource[_]](options: ListOptions, namespace: Option[String] = None)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] = {
  //    _list[L](rd, Some(options), namespace)
  //  }
  //
  //  private def _list[L <: ListResource[_]](maybeOptions: Option[ListOptions], namespace: Option[String])(implicit fmt: Format[L], lc: LoggingContext): Future[L] = {
  //    val queryOpt = maybeOptions map { opts =>
  //      Uri.Query(opts.asMap)
  //    }
  //    if (log.isDebugEnabled) {
  //      val optsInfo = maybeOptions map { opts => s" with options '${opts.asMap.toString}'" } getOrElse ""
  //      logDebug(s"[List request: resources of kind '${rd.spec.names.kind}'${optsInfo}")
  //    }
  //    val req = buildRequest(HttpMethods.GET, rd, None, query = queryOpt, namespace)
  //    makeRequestReturningListResource[L](req)
  //  }

  def getOption(name: String,
                namespace: Option[String] = None,
                apiVersion: String,
                resourcePlural: String)(implicit lc: LoggingContext): Future[Option[DynamicKubernetesObject]] = {
    _get(name, namespace, apiVersion, resourcePlural) map { result =>
      Some(result)
    } recover {
      case ex: K8SException if ex.status.code.contains(StatusCodes.NotFound.intValue) => None
    }
  }

  def get(name: String,
          namespace: Option[String] = None,
          apiVersion: String,
          resourcePlural: String)(implicit lc: LoggingContext): Future[DynamicKubernetesObject] = {
    _get(name, namespace, apiVersion, resourcePlural)
  }


  private[api] def _get(name: String,
                        namespace: Option[String],
                        apiVersion: String,
                        resourcePlural: String)(implicit lc: LoggingContext): Future[DynamicKubernetesObject] = {
    val req = buildRequest(HttpMethods.GET, apiVersion, resourcePlural, Some(name), namespace = namespace)
    makeRequestReturningObjectResource(req)
  }

  //  def delete[O <: ObjectResource](name: String, gracePeriodSeconds: Int = -1, namespace: Option[String] = None)(implicit rd: ResourceDefinition[O], lc: LoggingContext): Future[Unit] = {
  //    val grace = if (gracePeriodSeconds >= 0) Some(gracePeriodSeconds) else None
  //    val options = DeleteOptions(gracePeriodSeconds = grace)
  //    deleteWithOptions[O](name, options, namespace = namespace)
  //  }
  //
  //  def deleteWithOptions[O <: ObjectResource](name: String, options: DeleteOptions, namespace: Option[String] = None)(implicit rd: ResourceDefinition[O], lc: LoggingContext): Future[Unit] = {
  //    val marshalledOptions = Marshal(options)
  //    for {
  //      requestEntity <- marshalledOptions.to[RequestEntity]
  //      request = buildRequest(method = HttpMethods.DELETE, rd = rd, nameComponent = Some(name), namespace = namespace)
  //        .withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
  //      response <- invoke(request)
  //      responseStatusOpt <- checkResponseStatus(response)
  //      _ <- ignoreResponseBody(response, responseStatusOpt)
  //    } yield ()
  //  }
  //
  //  def deleteAll[L <: ListResource[_]](namespace: Option[String] = None)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] = {
  //    _deleteAll[L](rd, None, namespace)
  //  }

  //  def deleteAll[L <: ListResource[_]](implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] = {
  //    _deleteAll[L](rd, None, None)
  //  }
  //
  //  def deleteAllSelected[L <: ListResource[_]](labelSelector: LabelSelector, namespace: Option[String] = None)(implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] = {
  //    _deleteAll[L](rd, Some(labelSelector), namespace)
  //  }

  //  private def _deleteAll[L <: ListResource[_]](rd: ResourceDefinition[_], maybeLabelSelector: Option[LabelSelector], namespace: Option[String])(implicit fmt: Format[L], lc: LoggingContext): Future[L] = {
  //    val queryOpt = maybeLabelSelector map { ls =>
  //      Uri.Query("labelSelector" -> ls.toString)
  //    }
  //    if (log.isDebugEnabled) {
  //      val lsInfo = maybeLabelSelector map { ls => s" with label selector '${ls.toString}'" } getOrElse ""
  //      logDebug(s"[Delete request: resources of kind '${rd.spec.names.kind}'${lsInfo}")
  //    }
  //    val req = buildRequest(HttpMethods.DELETE, rd, None, query = queryOpt, namespace)
  //    makeRequestReturningListResource[L](req)
  //  }


  // The Watch methods place a Watch on the specified resource on the Kubernetes cluster.
  // The methods return Akka streams sources that will reactively emit a stream of updated
  // values of the watched resources.
  //  def watch[O <: ObjectResource](obj: O, namespace: Option[String])(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]] = {
  //    watch(name = obj.name, namespace = namespace)
  //  }
  //
  //  def watch[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]] = {
  //    watch(name = obj.name)
  //  }

  // The Watch methods place a Watch on the specified resource on the Kubernetes cluster.
  // The methods return Akka streams sources that will reactively emit a stream of updated
  // values of the watched resources.

  //  def watch[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]] = {
  //    Watch.events(this, name, sinceResourceVersion, bufSize, namespace)
  //  }

  // watch events on all objects of specified kind in current namespace
  //  def watchAll[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]] = {
  //    Watch.eventsOnKind[O](this, sinceResourceVersion, bufSize, namespace)
  //  }

  //  def watchContinuously[O <: ObjectResource](obj: O, namespace: Option[String])(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _] = {
  //    watchContinuously(name = obj.name, namespace = namespace)
  //  }

  //  def watchContinuously[O <: ObjectResource](obj: O)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _] = {
  //    watchContinuously(name = obj.name)
  //  }

  //  def watchContinuously[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _] = {
  //    val options = ListOptions(resourceVersion = sinceResourceVersion, timeoutSeconds = Some(300.minutes.toSeconds))
  //    WatchSource(this, buildLongPollingPool(), Some(name), options, bufSize, namespace)
  //  }

  //  def watchAllContinuously[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _] = {
  //    val options = ListOptions(resourceVersion = sinceResourceVersion, timeoutSeconds = Some(300.minutes.toSeconds))
  //    WatchSource(this, buildLongPollingPool(), None, options, bufSize, namespace)
  //  }
  //
  //  def watchWithOptions[O <: skuber.ObjectResource](options: ListOptions, bufsize: Int = 10000, namespace: Option[String] = None)(implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _] = {
  //    WatchSource(this, buildLongPollingPool(), None, options, bufsize, namespace)
  //  }

  private def buildLongPollingPool[O <: ObjectResource]() = {
    LongPollingPool[WatchSource.Start[O]](clusterServerUri.scheme,
      clusterServerUri.authority.host.address(),
      clusterServerUri.effectivePort,
      10.minutes,
      sslContext.map(ConnectionContext.httpsClient),
      poolSettings.connectionSettings.withIdleTimeout(10.minutes))
  }

  //  def patch[P <: Patch, O <: ObjectResource](name: String, patchData: P, namespace: Option[String] = None)
  //                                            (implicit patchfmt: Writes[P], fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext = RequestLoggingContext()): Future[O] = {
  //    val targetNamespace = namespace.orElse(Some(namespaceName))
  //    val contentType = patchData.strategy match {
  //      case StrategicMergePatchStrategy =>
  //        CustomMediaTypes.`application/strategic-merge-patch+json`
  //      case JsonMergePatchStrategy =>
  //        CustomMediaTypes.`application/merge-patch+json`
  //      case JsonPatchStrategy =>
  //        MediaTypes.`application/json-patch+json`
  //    }
  //    logInfo(logConfig.logRequestBasicMetadata, s"Requesting patch of resource: { name:$name ... }")
  //    logInfo(logConfig.logRequestFullObjectResource, s" Marshal and send: ${patchData.toString}")
  //    val marshal = Marshal(patchData)
  //    for {
  //      requestEntity <- marshal.to[RequestEntity]
  //      httpRequest = buildRequest(HttpMethods.PATCH, rd, Some(name), namespace = targetNamespace)
  //        .withEntity(requestEntity.withContentType(contentType))
  //      newOrUpdatedResource <- makeRequestReturningObjectResource[O](httpRequest)
  //    } yield newOrUpdatedResource
  //  }

  // get API versions supported by the cluster
  def getServerAPIVersions(implicit lc: LoggingContext): Future[List[String]] = {
    val url = clusterServer + "/api"
    val noAuthReq: HttpRequest = HttpRequest(method = HttpMethods.GET, uri = Uri(url))
    val request = HTTPRequestAuth.addAuth(noAuthReq, requestAuth)
    for {
      response <- invoke(request)
      apiVersionResource <- toKubernetesResponse(response)
    } yield apiVersionResource.jsonRaw.jsValue.as[List[String]]
  }


  def close: Unit = {
    isClosed = true
    closeHook foreach {
      _()
    } // invoke the specified close hook if specified
  }


  // check for non-OK status, returning (in a Future) some Status object if not ok or otherwise None
  private[skuber] def checkResponseStatus(response: HttpResponse): Future[Option[Status]] = {
    response.status.intValue match {
      case code if code < 300 =>
        Future.successful(None)
      case code =>
        // a non-success or unexpected status returned - we should normally have a Status in the response body
        val statusFut: Future[Status] = Unmarshal(response).to[Status]
        statusFut map { status =>
          if (log.isInfoEnabled)
            log.info(s"[Response: non-ok status returned - $status")
          Some(status)
        } recover { case ex =>
          if (log.isErrorEnabled)
            log.error(s"[Response: could not read Status for non-ok response, exception : ${ex.getMessage}]")
          val status: Status = Status(code = Some(response.status.intValue),
            message = Some("Non-ok response and unable to parse Status from response body to get further details"),
            details = Some(JsString(ex.getMessage)))
          Some(status)
        }
    }
  }

  /**
    * Discards the response
    * This is for requests (e.g. delete) for which we normally have no interest in the response body, but Akka Http
    * requires us to drain it anyway
    * (see https://doc.akka.io/docs/akka-http/current/scala/http/implications-of-streaming-http-entity.html)
    *
    * @param response the Http Response that we need to drain
    * @return A Future[Unit] that will be set to Success or Failure depending on outcome of draining
    */
  private def ignoreResponseBody(response: HttpResponse, responseStatusOpt: Option[Status]): Future[Unit] = {
    responseStatusOpt match {
      case Some(status) =>
        throw new K8SException(status)
      case _ =>
        response.discardEntityBytes().future.map(_ => ())
    }
  }
}

object DynamicKubernetesClientImpl {

  def build(k8sContext: Option[Context] = None,
            logConfig: Option[LoggingConfig] = None,
            closeHook: Option[() => Unit] = None,
            connectionPoolSettings: Option[ConnectionPoolSettings] = None)
           (implicit actorSystem: ActorSystem,
            executionContext: ExecutionContext): DynamicKubernetesClientImpl = {
    val logConfFinal = logConfig.getOrElse(LoggingConfig())
    val connectionPoolSettingsFinal = connectionPoolSettings.getOrElse(ConnectionPoolSettings(actorSystem))
    val k8sContextFinal = k8sContext.getOrElse(defaultK8sConfig.currentContext)
    new DynamicKubernetesClientImpl(k8sContextFinal, logConfFinal, closeHook, connectionPoolSettingsFinal)
  }
}
