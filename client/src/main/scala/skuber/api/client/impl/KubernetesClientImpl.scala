package skuber.api.client.impl

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}
import javax.net.ssl.SSLContext
import play.api.libs.json.{Format, Writes, Reads}
import skuber._
import skuber.api.client.exec.PodExecImpl
import skuber.api.client.{K8SException => _, _}
import skuber.api.security.{HTTPRequestAuth, TLS}
import skuber.api.watch.{LongPollingPool, Watch, WatchSource}
import skuber.json.PlayJsonSupportForAkkaHttp._
import skuber.json.format.apiobj.statusReads
import skuber.json.format.{apiVersionsFormat, deleteOptionsFmt, namespaceListFmt}
import skuber.api.patch._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}


/**
  * @author David O'Riordan
  * This class implements the KubernetesClient API. It uses the Akka HTTP client to handle the requests to
  * the Kubernetes API server.
  */
class KubernetesClientImpl private[client] (
  val requestMaker: (Uri, HttpMethod)  => HttpRequest, // builds the requests to send
  override val clusterServer: String, // the url of the target cluster Kubernetes API server
  val requestAuth: AuthInfo, // specifies the authentication (if any) to be added to requests
  override val namespaceName: String, // by default requests will target this specified namespace on the cluster
  val watchContinuouslyRequestTimeout: Duration,
  val watchContinuouslyIdleTimeout: Duration,
  val watchPoolIdleTimeout: Duration,
  val watchSettings: ConnectionPoolSettings,
  val podLogSettings: ConnectionPoolSettings,
  val sslContext: Option[SSLContext], // provides the Akka client with the SSL details needed for https connections to the API server
  override val logConfig: LoggingConfig,
  val closeHook: Option[() => Unit])(implicit val actorSystem: ActorSystem, val materializer: Materializer, val executionContext: ExecutionContext)
    extends KubernetesClient
{
  val log = Logging.getLogger(actorSystem, "skuber.api")

  val connectionContext = sslContext
      .map { ssl =>
        ConnectionContext.https(ssl, enabledProtocols = Some(scala.collection.immutable.Seq("TLSv1.2", "TLSv1")))
      }
      .getOrElse(Http().defaultClientHttpsContext)


  private val clusterServerUri = Uri(clusterServer)

  private var isClosed = false

  private[skuber] def invokeWatch(request: HttpRequest)(implicit lc: LoggingContext): Future[HttpResponse] = invoke(request, watchSettings)
  private[skuber] def invokeLog(request: HttpRequest)(implicit lc: LoggingContext): Future[HttpResponse] = invoke(request, podLogSettings)
  private[skuber] def invoke(request: HttpRequest, settings: ConnectionPoolSettings = ConnectionPoolSettings(actorSystem))(implicit lc: LoggingContext): Future[HttpResponse] = {
    if (isClosed) {
      logError("Attempt was made to invoke request on closed API request context")
      throw new IllegalStateException("Request context has been closed")
    }
    logInfo(logConfig.logRequestBasic, s"about to send HTTP request: ${request.method.value} ${request.uri.toString}")
    val responseFut = Http().singleRequest(request, settings = settings, connectionContext = connectionContext)
    responseFut onComplete {
      case Success(response) => logInfo(logConfig.logResponseBasic,s"received response with HTTP status ${response.status.intValue()}")
      case Failure(ex) => logError("HTTP request resulted in an unexpected exception",ex)
    }
    responseFut
  }

  private[skuber] def buildRequest[T <: TypeMeta](
    method: HttpMethod,
    rd: ResourceDefinition[_],
    nameComponent: Option[String],
    query: Option[Uri.Query] = None,
    namespace: String = namespaceName): HttpRequest =
  {
    val nsPathComponent = if (rd.spec.scope == ResourceSpecification.Scope.Namespaced) {
      Some("namespaces/" + namespace)
    } else {
      None
    }

    val k8sUrlOptionalParts = List(
      clusterServer,
      rd.spec.apiPathPrefix,
      rd.spec.group,
      rd.spec.defaultVersion,
      nsPathComponent,
      rd.spec.names.plural,
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

    val req = requestMaker(uri, method)
    HTTPRequestAuth.addAuth(req, requestAuth)
  }

  private[skuber] def logInfo(enabledLogEvent: Boolean, msg: => String)(implicit lc: LoggingContext) =
  {
    if (log.isInfoEnabled && enabledLogEvent) {
      log.info(s"[ ${lc.output} - ${msg}]")
    }
  }

  private[skuber] def logInfoOpt(enabledLogEvent: Boolean, msgOpt: => Option[String])(implicit lc: LoggingContext) =
  {
    if (log.isInfoEnabled && enabledLogEvent) {
      msgOpt foreach { msg =>
        log.info(s"[ ${lc.output} - ${msg}]")
      }
    }
  }

  private[skuber] def logWarn(msg: String)(implicit lc: LoggingContext) =
  {
    log.error(s"[ ${lc.output} - $msg ]")
  }

  private[skuber] def logError(msg: String)(implicit lc: LoggingContext) =
  {
    log.error(s"[ ${lc.output} - $msg ]")
  }

  private[skuber] def logError(msg: String, ex: Throwable)(implicit lc: LoggingContext) =
  {
    log.error(ex, s"[ ${lc.output} - $msg ]")
  }

  private[skuber] def logDebug(msg : => String)(implicit lc: LoggingContext) = {
    if (log.isDebugEnabled)
      log.debug(s"[ ${lc.output} - $msg ]")
  }

  private[skuber] def logRequestObjectDetails[O <: ObjectResource](method: HttpMethod,resource: O)(implicit lc: LoggingContext) = {
    logInfoOpt(logConfig.logRequestBasicMetadata, {
      val name = resource.name
      val version = resource.metadata.resourceVersion
      method match {
        case HttpMethods.PUT | HttpMethods.PATCH => Some(s"Requesting update of resource: { name:$name, version:$version ... }")
        case HttpMethods.POST => Some(s"Requesting creation of resource: { name: $name ...}")
        case _ => None
      }
    }
    )
    logInfo(logConfig.logRequestFullObjectResource, s" Marshal and send: ${resource.toString}")
  }

  private[skuber] def logReceivedObjectDetails[O <: ObjectResource](resource: O)(implicit lc: LoggingContext) =
  {
    logInfo(logConfig.logResponseBasicMetadata, s" resource: { kind:${resource.kind} name:${resource.name} version:${resource.metadata.resourceVersion} ... }")
    logInfo(logConfig.logResponseFullObjectResource, s" received and parsed: ${resource.toString}")
  }

  private[skuber] def logReceivedListDetails[L <: ListResource[_]](result: L)(implicit lc: LoggingContext) =
  {
    logInfo(logConfig.logResponseBasicMetadata,s"received list resource of kind ${result.kind}")
    logInfo(logConfig.logResponseListSize,s"number of items in received list resource: ${result.items.size}")
    logInfo(logConfig.logResponseListNames, s"received ${result.kind} contains item(s): ${result.itemNames}]")
    logInfo(logConfig.logResponseFullListResource, s" Unmarshalled list resource: ${result.toString}")
  }

  private[skuber] def makeRequestReturningObjectResource[O <: ObjectResource](httpRequest: HttpRequest)(
    implicit fmt: Format[O], lc: LoggingContext): Future[O] =
  {
    for {
      httpResponse <- invoke(httpRequest)
      result <- toKubernetesResponse[O](httpResponse)
      _ = logReceivedObjectDetails(result)
    } yield result
  }

  private[skuber] def makeRequestReturningListResource[L <: ListResource[_]](httpRequest: HttpRequest)(
    implicit fmt: Format[L], lc: LoggingContext): Future[L] =
  {
    for {
      httpResponse <- invoke(httpRequest)
      result <- toKubernetesResponse[L](httpResponse)
      _ = logReceivedListDetails(result)
    } yield result
  }

  /**
    * Modify the specified K8S resource using a given HTTP method. The modified resource is returned.
    * The create, update and partiallyUpdate methods all call this, just passing different HTTP methods
    */
  private[skuber] def modify[O <: ObjectResource](method: HttpMethod)(obj: O)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] =
  {
    // if this is a POST we don't include the resource name in the URL
    val nameComponent: Option[String] = method match {
      case HttpMethods.POST => None
      case _                => Some(obj.name)
    }
    modify(method, obj, nameComponent)
  }

  private[skuber] def  modify[O <: ObjectResource](method: HttpMethod, obj: O, nameComponent: Option[String])(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] =
  {
    // Namespace set in the object metadata (if set) has higher priority than that of the
    // request context (see Issue #204)
    val targetNamespace = if (obj.metadata.namespace.isEmpty) namespaceName else obj.metadata.namespace

    logRequestObjectDetails(method, obj)
    val marshal = Marshal(obj)
    for {
      requestEntity        <- marshal.to[RequestEntity]
      httpRequest          = buildRequest(method, rd, nameComponent, namespace = targetNamespace)
          .withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
      newOrUpdatedResource <- makeRequestReturningObjectResource[O](httpRequest)
    } yield newOrUpdatedResource
  }

  override def create[O <: ObjectResource](obj: O)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] =
  {
    modify(HttpMethods.POST)(obj)
  }

  override def update[O <: ObjectResource](obj: O)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] =
  {
    modify(HttpMethods.PUT)(obj)
  }

  override def updateStatus[O <: ObjectResource](obj: O)(implicit
    fmt: Format[O],
    rd: ResourceDefinition[O],
    statusEv: HasStatusSubresource[O],
    lc: LoggingContext): Future[O] =
  {
    val statusSubresourcePath=s"${obj.name}/status"
    modify(HttpMethods.PUT,obj,Some(statusSubresourcePath))
  }

  override def getStatus[O <: ObjectResource](name: String)(implicit
    fmt: Format[O],
    rd: ResourceDefinition[O],
    statusEv: HasStatusSubresource[O],
    lc: LoggingContext): Future[O] =
  {
    _get[O](s"${name}/status")
  }

  override def getNamespaceNames(implicit lc: LoggingContext): Future[List[String]] =
  {
    list[NamespaceList].map { namespaceList =>
      val namespaces = namespaceList.items
      namespaces.map(_.name)
    }
  }

  /*
  * List by namespace returns a map of namespace (specified by name e.g. "default", "kube-sys") to the list of objects
  * of the specified kind in said namespace. All namespaces in the cluster are included in the map.
  * For example, it can be used to get a single list of all objects of the given kind across the whole cluster
  * e.g. val allPodsInCluster: Future[List[Pod]] = listByNamespace[Pod] map { _.values.flatMap(_.items) }
  * which supports the feature requested in issue #20
   */
  override def listByNamespace[L <: ListResource[_]]()(
    implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[Map[String, L]] =
  {
    listByNamespace[L](rd)
  }

  private def listByNamespace[L <: ListResource[_]](rd: ResourceDefinition[_])
      (implicit fmt: Format[L], lc: LoggingContext): Future[Map[String, L]] =
  {
    val nsNamesFut: Future[List[String]] = getNamespaceNames
    val tuplesFut: Future[List[(String, L)]] = nsNamesFut flatMap { nsNames: List[String] =>
      Future.sequence(nsNames map { (nsName: String) =>
        listInNamespace[L](nsName, rd) map { l => (nsName, l) }
      })
    }
    tuplesFut map {
      _.toMap[String, L]
    }
  }

  /*
   * List all objects of given kind in the specified namespace on the cluster
   */
  override def listInNamespace[L <: ListResource[_]](theNamespace: String)(
    implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] =
  {
    listInNamespace[L](theNamespace, rd)
  }

  private def listInNamespace[L <: ListResource[_]](theNamespace: String, rd: ResourceDefinition[_])(
    implicit fmt: Format[L], lc: LoggingContext): Future[L] =
  {
    val req = buildRequest(HttpMethods.GET, rd, None, namespace = theNamespace)
    makeRequestReturningListResource[L](req)
  }

  /*
   * List objects of specific resource kind in current namespace
   */
  override def list[L <: ListResource[_]]()(
    implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] =
  {
    _list[L](rd, None)
  }

  /*
   * Retrieve the list of objects of given type in the current namespace that match the supplied label selector
   */
  override def listSelected[L <: ListResource[_]](labelSelector: LabelSelector)(
    implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] =
  {
    _list[L](rd, Some(ListOptions(labelSelector=Some(labelSelector))))
  }

  override def listWithOptions[L <: ListResource[_]](options: ListOptions)(
   implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] =
  {
    _list[L](rd, Some(options))
  }

  private def _list[L <: ListResource[_]](rd: ResourceDefinition[_], maybeOptions: Option[ListOptions])(
    implicit fmt: Format[L], lc: LoggingContext): Future[L] =
  {
    val queryOpt = maybeOptions map { opts =>
      Uri.Query(opts.asMap)
    }
    if (log.isDebugEnabled) {
      val optsInfo = maybeOptions map { opts => s" with options '${opts.asMap.toString}'" } getOrElse ""
      logDebug(s"[List request: resources of kind '${rd.spec.names.kind}'${optsInfo}")
    }
    val req = buildRequest(HttpMethods.GET, rd, None, query = queryOpt)
    makeRequestReturningListResource[L](req)
  }

  override def getOption[O <: ObjectResource](name: String)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Option[O]] =
  {
    _get[O](name) map { result =>
      Some(result)
    } recover {
      case ex: K8SException if ex.status.code.contains(StatusCodes.NotFound.intValue) => None
    }
  }

  override def get[O <: ObjectResource](name: String)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] =
  {
    _get[O](name)
  }

  override def getInNamespace[O <: ObjectResource](name: String, namespace: String)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] =
  {
    _get[O](name, namespace)
  }

  private[api] def _get[O <: ObjectResource](name: String, namespace: String = namespaceName)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] =
  {
    val req = buildRequest(HttpMethods.GET, rd, Some(name), namespace = namespace)
    makeRequestReturningObjectResource[O](req)
  }

  override def delete[O <: ObjectResource](name: String, gracePeriodSeconds: Int = -1)(
    implicit rd: ResourceDefinition[O], lc: LoggingContext): Future[Unit] =
  {
    val grace=if (gracePeriodSeconds >= 0) Some(gracePeriodSeconds) else None
    val options = DeleteOptions(gracePeriodSeconds = grace)
    deleteWithOptions[O](name, options)
  }

  override def deleteWithOptions[O <: ObjectResource](name: String, options: DeleteOptions)(
    implicit rd: ResourceDefinition[O], lc: LoggingContext): Future[Unit] =
  {
    val marshalledOptions = Marshal(options)
    for {
      requestEntity <- marshalledOptions.to[RequestEntity]
      request       = buildRequest(HttpMethods.DELETE, rd, Some(name))
          .withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
      response      <- invoke(request)
      _             <- checkResponseStatus(response)
      _             <- ignoreResponseBody(response)
    } yield ()
  }

  override def deleteAll[L <: ListResource[_]]()(
    implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] =
  {
    _deleteAll[L](rd, None)
  }

  override def deleteAllSelected[L <: ListResource[_]](labelSelector: LabelSelector)(
    implicit fmt: Format[L], rd: ResourceDefinition[L], lc: LoggingContext): Future[L] =
  {
    _deleteAll[L](rd, Some(labelSelector))
  }

  private def _deleteAll[L <: ListResource[_]](rd: ResourceDefinition[_], maybeLabelSelector: Option[LabelSelector])(
    implicit fmt: Format[L], lc: LoggingContext): Future[L] =
  {
    val queryOpt = maybeLabelSelector map { ls =>
      Uri.Query("labelSelector" -> ls.toString)
    }
    if (log.isDebugEnabled) {
      val lsInfo = maybeLabelSelector map { ls => s" with label selector '${ls.toString}'" } getOrElse ""
      logDebug(s"[Delete request: resources of kind '${rd.spec.names.kind}'${lsInfo}")
    }
    val req = buildRequest(HttpMethods.DELETE, rd, None, query = queryOpt)
    makeRequestReturningListResource[L](req)
  }

  override def getPodLogSource(name: String, queryParams: Pod.LogQueryParams, namespace: Option[String] = None)(
    implicit lc: LoggingContext): Future[Source[ByteString, _]] =
  {
    val targetNamespace=namespace.getOrElse(this.namespaceName)
    val queryMap=queryParams.asMap
    val query: Option[Uri.Query] = if (queryMap.isEmpty) {
      None
    } else {
      Some(Uri.Query(queryMap))
    }
    val nameComponent=s"${name}/log"
    val rd = implicitly[ResourceDefinition[Pod]]
    val request = buildRequest(HttpMethods.GET, rd, Some(nameComponent), query, targetNamespace)
    invokeLog(request).flatMap { response =>
      val statusOptFut = checkResponseStatus(response)
      statusOptFut map {
        case Some(status) =>
          throw new K8SException(status)
        case _ =>
          response.entity.dataBytes
      }
    }
  }


  // The Watch methods place a Watch on the specified resource on the Kubernetes cluster.
  // The methods return Akka streams sources that will reactively emit a stream of updated
  // values of the watched resources.

  override def watch[O <: ObjectResource](obj: O)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]] =
  {
    watch(obj.name)
  }

  // The Watch methods place a Watch on the specified resource on the Kubernetes cluster.
  // The methods return Akka streams sources that will reactively emit a stream of updated
  // values of the watched resources.

  override def watch[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]] =
  {
    Watch.events(this, name, sinceResourceVersion, bufSize)
  }

  // watch events on all objects of specified kind in current namespace
  override def watchAll[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[Source[WatchEvent[O], _]] =
  {
    Watch.eventsOnKind[O](this, sinceResourceVersion, bufSize)
  }

  override def watchContinuously[O <: ObjectResource](obj: O)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _] =
  {
    watchContinuously(obj.name)
  }

  override def watchContinuously[O <: ObjectResource](name: String, sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _] =
  {
    val options=ListOptions(resourceVersion = sinceResourceVersion, timeoutSeconds = Some(watchContinuouslyRequestTimeout.toSeconds) )
    WatchSource(this, buildLongPollingPool(), Some(name), options, bufSize)
  }

  override def watchAllContinuously[O <: ObjectResource](sinceResourceVersion: Option[String] = None, bufSize: Int = 10000)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _] =
  {
    val options=ListOptions(resourceVersion = sinceResourceVersion, timeoutSeconds = Some(watchContinuouslyRequestTimeout.toSeconds))
    WatchSource(this, buildLongPollingPool(), None, options, bufSize)
  }

  override def watchWithOptions[O <: skuber.ObjectResource](options: ListOptions, bufsize: Int = 10000)(
    implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], _] =
  {
    WatchSource(this, buildLongPollingPool(), None, options, bufsize)
  }

  private def buildLongPollingPool[O <: ObjectResource]() = {
    LongPollingPool[WatchSource.Start[O]](
      clusterServerUri.scheme,
      clusterServerUri.authority.host.address(),
      clusterServerUri.effectivePort,
      watchPoolIdleTimeout,
      sslContext.map(new HttpsConnectionContext(_)),
      ClientConnectionSettings(actorSystem.settings.config).withIdleTimeout(watchContinuouslyIdleTimeout)
    )
  }

  // Operations on scale subresource
  // Scale subresource Only exists for certain resource types like RC, RS, Deployment, StatefulSet so only those types
  // define an implicit Scale.SubresourceSpec, which is required to be passed to these methods.
  override def getScale[O <: ObjectResource](objName: String)(
    implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc: LoggingContext) : Future[Scale] =
  {
    val req = buildRequest(HttpMethods.GET, rd, Some(objName+ "/scale"))
    makeRequestReturningObjectResource[Scale](req)
  }

  @deprecated("use getScale followed by updateScale instead")
  override def scale[O <: ObjectResource](objName: String, count: Int)(
    implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc: LoggingContext): Future[Scale] =
  {
    val scale = Scale(
      apiVersion = sc.apiVersion,
      metadata = ObjectMeta(name = objName, namespace = namespaceName),
      spec = Scale.Spec(replicas = count)
    )
    updateScale[O](objName, scale)
  }

  override def updateScale[O <: ObjectResource](objName: String, scale: Scale)(
    implicit rd: ResourceDefinition[O], sc: Scale.SubresourceSpec[O], lc:LoggingContext): Future[Scale] =
  {
    implicit val dispatcher = actorSystem.dispatcher
    val marshal = Marshal(scale)
    for {
      requestEntity  <- marshal.to[RequestEntity]
      httpRequest    = buildRequest(HttpMethods.PUT, rd, Some(s"${objName}/scale"))
          .withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
      scaledResource <- makeRequestReturningObjectResource[Scale](httpRequest)
    } yield scaledResource
  }

  override def patch[P <: Patch, O <: ObjectResource](name: String, patchData: P, namespace: Option[String] = None)
      (implicit patchfmt: Writes[P], fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext = RequestLoggingContext()): Future[O] = {
    val targetNamespace = namespace.getOrElse(namespaceName)
    val contentType = patchData.strategy match {
      case StrategicMergePatchStrategy =>
        CustomMediaTypes.`application/strategic-merge-patch+json`
      case JsonMergePatchStrategy =>
        CustomMediaTypes.`application/merge-patch+json`
      case JsonPatchStrategy =>
        MediaTypes.`application/json-patch+json`
    }
    logInfo(logConfig.logRequestBasicMetadata, s"Requesting patch of resource: { name:$name ... }")
    logInfo(logConfig.logRequestFullObjectResource, s" Marshal and send: ${patchData.toString}")
    val marshal = Marshal(patchData)
    for {
      requestEntity <- marshal.to[RequestEntity]
      httpRequest = buildRequest(HttpMethods.PATCH, rd, Some(name), namespace = targetNamespace)
          .withEntity(requestEntity.withContentType(contentType))
      newOrUpdatedResource <- makeRequestReturningObjectResource[O](httpRequest)
    } yield newOrUpdatedResource
  }

  /**
    * Perform a Json merge patch on a resource
    * The patch is passed a String type which should contain the JSON patch formatted per https://tools.ietf.org/html/rfc7386
    * It is a String type instead of a JSON object in order to allow clients to use their own favourite JSON library to create the
    * patch, or alternatively to simply manually craft the JSON and insert it into a String.  Also patches are generally expected to be
    * relatively small, so storing the whole patch in memory should not be problematic.
    * It is thus the responsibility of the client to ensure that the `patch` parameter contains a valid JSON merge patch entity for the
    * targetted Kubernetes resource `obj`
    * @param obj The resource to update with the patch
    * @param patch A string containing the JSON patch entity
    * @return The patched resource (in a Future)
    */
  override def jsonMergePatch[O <: ObjectResource](obj: O, patch: String)(
    implicit rd: ResourceDefinition[O], fmt: Format[O], lc:LoggingContext): Future[O] =
  {
    val patchRequestEntity = HttpEntity.Strict(`application/merge-patch+json`, ByteString(patch))
    val httpRequest = buildRequest(HttpMethods.PATCH, rd, Some(obj.name)).withEntity(patchRequestEntity)
    makeRequestReturningObjectResource[O](httpRequest)
  }

  // get API versions supported by the cluster
  override def getServerAPIVersions(implicit lc: LoggingContext): Future[List[String]] = {
    val url = clusterServer + "/api"
    val noAuthReq = requestMaker(Uri(url), HttpMethods.GET)
    val request = HTTPRequestAuth.addAuth(noAuthReq, requestAuth)
    for {
      response <- invoke(request)
      apiVersionResource <- toKubernetesResponse[APIVersions](response)
    } yield apiVersionResource.versions
  }

  /*
   * Execute a command in a pod
   */
  override def exec(
    podName: String,
    command: Seq[String],
    maybeContainerName: Option[String] = None,
    maybeStdin: Option[Source[String, _]] = None,
    maybeStdout: Option[Sink[String, _]] = None,
    maybeStderr: Option[Sink[String, _]] = None,
    tty: Boolean = false,
    maybeClose: Option[Promise[Unit]] = None)(implicit lc:  LoggingContext): Future[Unit] =
  {
    PodExecImpl.exec(this, podName, command, maybeContainerName, maybeStdin, maybeStdout, maybeStderr, tty, maybeClose)
  }

  override def close: Unit =
  {
    isClosed = true
    closeHook foreach {
      _ ()
    } // invoke the specified close hook if specified
  }

  /*
   * Lightweight switching of namespace for applications that need to access multiple namespaces on same cluster
   * and using same credentials and other configuration.
   */
  override def usingNamespace(newNamespace: String): KubernetesClientImpl =
    new KubernetesClientImpl(requestMaker, clusterServer, requestAuth,
      newNamespace, watchContinuouslyRequestTimeout,  watchContinuouslyIdleTimeout,
      watchPoolIdleTimeout, watchSettings, podLogSettings, sslContext, logConfig, closeHook
    )

  private[skuber] def toKubernetesResponse[T](response: HttpResponse)(implicit reader: Reads[T], lc: LoggingContext): Future[T] =
  {
    val statusOptFut = checkResponseStatus(response)
    statusOptFut flatMap {
      case Some(status) =>
        throw new K8SException(status)
      case None =>
        try {
          Unmarshal(response).to[T]
        }
        catch {
          case ex: Exception =>
            logError("Unable to unmarshal resource from response", ex)
            throw new K8SException(Status(message = Some("Error unmarshalling resource from response"), details = Some(ex.getMessage)))
        }
    }
  }

  // check for non-OK status, returning (in a Future) some Status object if not ok or otherwise None
  private[skuber] def checkResponseStatus(response: HttpResponse)(implicit lc: LoggingContext): Future[Option[Status]] =
  {
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
          val status: Status = Status(
            code = Some(response.status.intValue),
            message = Some("Non-ok response and unable to parse Status from response body to get further details"),
            details = Some(ex.getMessage)
          )
          Some(status)
        }
    }
  }

  /**
    * Discards the response
    * This is for requests (e.g. delete) for which we normally have no interest in the response body, but Akka Http
    * requires us to drain it anyway
    * (see https://doc.akka.io/docs/akka-http/current/scala/http/implications-of-streaming-http-entity.html)
    * @param response the Http Response that we need to drain
    * @return A Future[Unit] that will be set to Success or Failure depending on outcome of draining
    */
  private def ignoreResponseBody(response: HttpResponse): Future[Unit] = {
    response.discardEntityBytes().future.map(done => ())
  }
}

object KubernetesClientImpl {

  def apply(k8sContext: Context, logConfig: LoggingConfig, closeHook: Option[() => Unit], appConfig: Config)
   (implicit actorSystem: ActorSystem, materializer: Materializer): KubernetesClientImpl =
  {
    appConfig.checkValid(ConfigFactory.defaultReference(), "skuber")

    def getSkuberConfig[T](key: String, fromConfig: String => Option[T], default: T): T = {
      val skuberConfigKey = s"skuber.$key"
      if (appConfig.getIsNull(skuberConfigKey)) {
        default
      } else {
        fromConfig(skuberConfigKey) match {
          case None => default
          case Some(t) => t
        }
      }
    }

    def dispatcherFromConfig(configKey: String): Option[ExecutionContext] = if (appConfig.getString(configKey).isEmpty) {
      None
    } else {
      Some(actorSystem.dispatchers.lookup(appConfig.getString(configKey)))
    }

    implicit val dispatcher: ExecutionContext = getSkuberConfig("akka.dispatcher", dispatcherFromConfig, actorSystem.dispatcher)

    def durationFomConfig(configKey: String): Option[Duration] = Some(Duration.fromNanos(appConfig.getDuration(configKey).toNanos))

    val watchIdleTimeout: Duration = getSkuberConfig("watch.idle-timeout", durationFomConfig, Duration.Inf)
    val podLogIdleTimeout: Duration = getSkuberConfig("pod-log.idle-timeout", durationFomConfig, Duration.Inf)

    val watchContinuouslyRequestTimeout: Duration = getSkuberConfig("watch-continuously.request-timeout", durationFomConfig, 30.seconds)
    val watchContinuouslyIdleTimeout: Duration = getSkuberConfig("watch-continuously.idle-timeout", durationFomConfig, 60.seconds)
    val watchPoolIdleTimeout: Duration = getSkuberConfig("watch-continuously.pool-idle-timeout", durationFomConfig, 60.seconds)

    //The watch idle timeout needs to be greater than watch api request timeout
    require(watchContinuouslyIdleTimeout > watchContinuouslyRequestTimeout)

    if (logConfig.logConfiguration) {
      val log = Logging.getLogger(actorSystem, "skuber.api")
      log.info("Using following context for connecting to Kubernetes cluster: {}", k8sContext)
    }

    val sslContext = TLS.establishSSLContext(k8sContext)

    val theNamespaceName = k8sContext.namespace.name match {
      case "" => "default"
      case name => name
    }

    val requestMaker = (uri: Uri, method: HttpMethod) => HttpRequest(method = method, uri = uri)

    val defaultClientSettings = ConnectionPoolSettings(actorSystem.settings.config)
    val watchConnectionSettings = defaultClientSettings.connectionSettings.withIdleTimeout(watchIdleTimeout)
    val watchSettings = defaultClientSettings.withConnectionSettings(watchConnectionSettings)

    val podLogConnectionSettings = defaultClientSettings.connectionSettings.withIdleTimeout(podLogIdleTimeout)
    val podLogSettings = defaultClientSettings.withConnectionSettings(podLogConnectionSettings)

    new KubernetesClientImpl(
      requestMaker, k8sContext.cluster.server, k8sContext.authInfo,
      theNamespaceName, watchContinuouslyRequestTimeout, watchContinuouslyIdleTimeout,
      watchPoolIdleTimeout, watchSettings, podLogSettings, sslContext, logConfig, closeHook
    )
  }
}
