package skuber.api.dynamic.client.impl

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.Logging
import org.apache.pekko.http.scaladsl.marshalling.{Marshal, Marshaller, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import org.apache.pekko.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import play.api.libs.json.{JsString, JsValue}
import skuber.{DeleteOptions, ListOptions}
import skuber.api.client._
import skuber.api.security.{HTTPRequestAuth, TLS}
import skuber.json.PlayJsonSupportForPekkoHttp._
import skuber.json.format.apiobj.statusReads
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import skuber.json.format.deleteOptionsFmt
import DynamicKubernetesClientImpl.jsValueToRequestEntityMarshaller
import org.apache.pekko.util.ByteString
/**
  * This is non-typed kubernetes client, for typed client see [[skuber.api.client.impl.KubernetesClientImpl]]
  * This class provides a dynamic client for the Kubernetes API server.
  * It is intended to be used for accessing resources / classes that are not part of the skuber library.
  *
  * It uses the Pekko HTTP client to handle the requests to
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

  /**
    * Get a resource from the Kubernetes API server
    *
    * @param name           is the name of the resource to retrieve
    * @param apiVersion     is the api version of the resource type to retrieve, e.g: "apps/v1"
    * @param resourcePlural is the plural name of the resource type to retrieve: e.g: "pods", "deployments"
    * @param namespace      is the namespace of the resource to retrieve
    * */
  def get(name: String,
          apiVersion: String,
          resourcePlural: String,
          namespace: Option[String] = None)(implicit lc: LoggingContext): Future[DynamicKubernetesObject] = {
    _get(name, namespace, apiVersion, resourcePlural)
  }

  /**
    * Get a resource from the Kubernetes API server
    *
    * @param name           is the name of the resource to retrieve
    * @param namespace      is the namespace of the resource to retrieve
    * @param apiVersion     is the api version of the resource type to retrieve, e.g: "apps/v1"
    * @param resourcePlural is the plural name of the resource type to retrieve: e.g: "pods", "deployments"
    * */
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

  /**
    * Create a kubernetes resource
    *
    * @param rawInput       is the raw json input of the object to create
    * @param namespace      is the namespace of the resource
    * @param resourcePlural is the plural name of the resource type: e.g: "pods", "deployments"
    * */
  def create(rawInput: JsonRaw, namespace: Option[String] = None, resourcePlural: String): Future[DynamicKubernetesObject] = {
    modify(
      method = HttpMethods.POST,
      rawInput = rawInput,
      namespace = namespace,
      resourcePlural = resourcePlural
    )
  }

  /**
    * Update a resource from the Kubernetes API server
    *
    * @param rawInput       is the raw json input of the object to create
    * @param namespace      is the namespace of the resource
    * @param resourcePlural is the plural name of the resource type: e.g: "pods", "deployments"
    * */
  def update(rawInput: JsonRaw, namespace: Option[String] = None, resourcePlural: String): Future[DynamicKubernetesObject] = {
    modify(
      method = HttpMethods.PUT,
      rawInput = rawInput,
      namespace = namespace,
      resourcePlural = resourcePlural
    )
  }

  /**
    * List objects of specific resource kind in current namespace
    *
    * @param apiVersion     is the api version of the resource type to retrieve, e.g: "apps/v1"
    * @param resourcePlural is the plural name of the resource type to retrieve: e.g: "pods", "deployments"
    * @param namespace      is the namespace of the resource
    * @param options        see [[ListOptions]]
    */
  def list(apiVersion: String,
           resourcePlural: String,
           namespace: Option[String] = None,
           options: Option[ListOptions] = None): Future[DynamicKubernetesObjectList] = {
    val queryOpt = options map { opts =>
      Uri.Query(opts.asMap)
    }
    val req = buildRequest(method = HttpMethods.GET,
      apiVersion = apiVersion,
      resourcePlural = resourcePlural,
      query = queryOpt,
      nameComponent = None,
      namespace = namespace)
    makeRequestReturningObjectResource[DynamicKubernetesObjectList](req)
  }

  /**
    * Delete a resource from the Kubernetes API server
    *
    * @param name           resource name
    * @param namespace      is the namespace of the resource
    * @param apiVersion     is the api version of the resource type to retrieve, e.g: "apps/v1"
    * @param resourcePlural is the plural name of the resource type to retrieve: e.g: "pods", "deployments"
    * */
  def delete(name: String, namespace: Option[String] = None, apiVersion: String, resourcePlural: String): Future[Unit] = {
    val options = DeleteOptions()
    deleteWithOptions(name, options, namespace = namespace, apiVersion = apiVersion, resourcePlural = resourcePlural)
  }

  /**
    * Delete a resource from the Kubernetes API server
    *
    * @param name           resource name
    * @param options        delete options see [[DeleteOptions]]
    * @param namespace      is the namespace of the resource
    * @param apiVersion     is the api version of the resource type to retrieve, e.g: "apps/v1"
    * @param resourcePlural is the plural name of the resource type to retrieve: e.g: "pods", "deployments"
    * */
  def deleteWithOptions(name: String, options: DeleteOptions, apiVersion: String, resourcePlural: String, namespace: Option[String] = None): Future[Unit] = {
    val marshalledOptions = Marshal(options)
    for {
      requestEntity <- marshalledOptions.to[RequestEntity]
      request = buildRequest(method = HttpMethods.DELETE, apiVersion = apiVersion, resourcePlural = resourcePlural, nameComponent = Some(name), namespace = namespace)
        .withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
      response <- invoke(request)
      responseStatusOpt <- checkResponseStatus(response)
      _ <- ignoreResponseBody(response, responseStatusOpt)
    } yield ()
  }

  // get API versions supported by the cluster
  def getServerAPIVersions(implicit lc: LoggingContext): Future[List[String]] = {
    val url = clusterServer + "/api"
    val noAuthReq: HttpRequest = HttpRequest(method = HttpMethods.GET, uri = Uri(url))
    val request = HTTPRequestAuth.addAuth(noAuthReq, requestAuth)
    for {
      response <- invoke(request)
      apiVersionResource <- toKubernetesResponse[DynamicKubernetesObject](response)
    } yield apiVersionResource.jsonRaw.jsValue.as[List[String]]
  }


  private def modify(method: HttpMethod,
                     rawInput: JsonRaw,
                     resourcePlural: String,
                     namespace: Option[String])(implicit lc: LoggingContext, um: Unmarshaller[HttpResponse, DynamicKubernetesObject]): Future[DynamicKubernetesObject] = {
    // if this is a POST we don't include the resource name in the URL
    val nameComponent: Option[String] = method match {
      case HttpMethods.POST => None
      case _ => (rawInput.jsValue \ "metadata" \ "name").asOpt[String]
    }
    val apiVersion = (rawInput.jsValue \ "apiVersion").asOpt[String].getOrElse(throw new Exception(s"apiVersion not specified in raw input: $rawInput"))

    val marshal = Marshal(rawInput.jsValue)
    for {
      requestEntity <- marshal.to[RequestEntity]
      httpRequest = buildRequest(method, apiVersion, resourcePlural, nameComponent, namespace = namespace)
        .withEntity(requestEntity.withContentType(MediaTypes.`application/json`))
      newOrUpdatedResource <- makeRequestReturningObjectResource[DynamicKubernetesObject](httpRequest)
    } yield newOrUpdatedResource
  }

  private[skuber] def invoke(request: HttpRequest)(implicit lc: LoggingContext): Future[HttpResponse] = {
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

  private[skuber] def logInfo(enabledLogEvent: Boolean, msg: => String)(implicit lc: LoggingContext): Unit = {
    if (log.isInfoEnabled && enabledLogEvent) {
      log.info(s"[ ${lc.output} - ${msg}]")
    }
  }

  private[skuber] def logError(msg: String, ex: Throwable)(implicit lc: LoggingContext): Unit = {
    log.error(ex, s"[ ${lc.output} - $msg ]")
  }

  private[skuber] def makeRequestReturningObjectResource[T](httpRequest: HttpRequest)(implicit lc: LoggingContext, um: Unmarshaller[HttpResponse, T]): Future[T] = {
    for {
      httpResponse <- invoke(httpRequest)
      result <- toKubernetesResponse[T](httpResponse)
    } yield result
  }


  private[skuber] def toKubernetesResponse[T](response: HttpResponse)(implicit lc: LoggingContext, um: Unmarshaller[HttpResponse, T]): Future[T] = {
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
            throw new K8SException(Status(message = Some("Error unmarshalling resource from response"), details = Some(JsString(ex.getMessage))))
        }
    }
  }

  private[api] def _get(name: String,
                        namespace: Option[String],
                        apiVersion: String,
                        resourcePlural: String)(implicit lc: LoggingContext): Future[DynamicKubernetesObject] = {
    val req = buildRequest(HttpMethods.GET, apiVersion, resourcePlural, Some(name), namespace = namespace)
    makeRequestReturningObjectResource[DynamicKubernetesObject](req)
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
    * This is for requests (e.g. delete) for which we normally have no interest in the response body, but Pekko Http
    * requires us to drain it anyway
    * (see https://doc.pekko.io/docs/pekko-http/current/scala/http/implications-of-streaming-http-entity.html)
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


  implicit val jsValueToRequestEntityMarshaller: ToEntityMarshaller[JsValue] =
    Marshaller.withFixedContentType(MediaTypes.`application/json`) { jsValue =>
      val jsonString = jsValue.toString()
      HttpEntity.Strict(MediaTypes.`application/json`, ByteString(jsonString))
    }

}
